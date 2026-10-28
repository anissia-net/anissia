package anissia.wiki.upload

import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import anissia.support.FailException
import anissia.wiki.WikiAccessService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.util.UUID
import java.util.concurrent.Semaphore

@Service
class WikiImageService(
    private val repository: WikiImageRepository,
    private val storage: WikiImageStorage,
    private val access: WikiAccessService,
    private val properties: WikiUploadProperties,
    private val fileOperations: WikiImageFileOperations,
    private val logs: ActivePanelLogService,
) {
    private val receiving = Semaphore(2)

    fun upload(file: MultipartFile, description: String, sourceUrl: String, actor: Actor): WikiImageDto {
        if (!properties.enabled) throw FailException("이미지 업로드가 준비 중입니다.")
        val account = access.requireContributor(actor)
        val text = description.trim()
        val source = sourceUrl.trim()
        validateMetadata(text, source)
        require(!file.isEmpty && file.size <= properties.maxBytes) { "이미지는 최대 ${properties.maxBytes / (1024 * 1024)}MB까지 업로드할 수 있습니다." }
        if (!receiving.tryAcquire()) throw FailException("업로드가 많습니다. 잠시 후 다시 시도해 주세요.")
        val id = UUID.randomUUID().toString().replace("-", "")
        try {
            file.inputStream.use { storage.receive(id, it) }
            return dto(repository.enqueue(id, account.an, text, source, properties))
        } catch (e: Exception) {
            storage.removeTemporary(id)
            throw e
        } finally {
            receiving.release()
        }
    }

    fun get(id: String, actor: Actor): WikiImageDto {
        val image = find(id)
        if (image.status in PRIVATE_STATES) {
            access.requireOperator(actor)
        } else if (image.status != WikiImageStatus.READY) {
            if (image.ownerAn != null && image.ownerAn == actor.an) access.requireContributor(actor)
            else access.requireOperator(actor)
        }
        return dto(image)
    }

    fun list(page: Int, query: String, deleted: Boolean = false, actor: Actor = Actor()): WikiImagePage {
        if (deleted) access.requireOperator(actor)
        require(page in 0..100_000 && query.length <= 100) { "잘못된 이미지 검색 요청입니다." }
        val (images, count) = repository.list(page, query.trim(), deleted)
        return WikiImagePage(images.map(::dto), count, page)
    }

    fun edit(id: String, request: WikiImageEditRequest, actor: Actor): WikiImageDto {
        val account = access.requireContributor(actor)
        find(id)
        val description = request.description.trim()
        val source = request.sourceUrl.trim()
        validateMetadata(description, source)
        return dto(repository.edit(id, request.baseRevision, description, source, account.name) {
            logs.addText("[${account.name}]님이 위키 이미지 [$id]의 설명·출처를 수정하였습니다.", actor = Actor(an = account.an, name = account.name))
        })
    }

    fun usage(id: String, page: Int, actor: Actor): WikiImageUsagePage {
        val image = get(id, actor)
        require(page in 0..100_000) { "잘못된 사용처 조회입니다." }
        return repository.usage(storage.publicUrl(id), page, image.status == WikiImageStatus.READY, access.access(actor).isOperator)
    }

    fun delete(id: String, request: WikiImageOperationRequest, actor: Actor): WikiImageDto {
        val account = access.requireOperator(actor)
        find(id)
        repository.beginDelete(id, request.baseRevision, account.name, storage.publicUrl(id)) {
            logs.addText("[${account.name}]님이 위키 이미지 [$id]의 삭제를 요청하였습니다.", actor = Actor(an = account.an, name = account.name))
        }
        fileOperations.process(id)
        return dto(find(id))
    }

    fun restore(id: String, request: WikiImageOperationRequest, actor: Actor): WikiImageDto {
        val account = access.requireOperator(actor)
        find(id)
        repository.beginRestore(id, request.baseRevision, account.name) {
            logs.addText("[${account.name}]님이 위키 이미지 [$id]의 복구를 요청하였습니다.", actor = Actor(an = account.an, name = account.name))
        }
        fileOperations.process(id)
        return dto(find(id))
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun requireReadyUrls(urls: Set<String>) {
        require(urls.size <= 5100) { "템플릿을 포함한 이미지 수가 제한을 초과했습니다." }
        val prefix = "${properties.publicBaseUrl.trimEnd('/')}/wiki/"
        urls.sorted().forEach { url ->
            val id = url.takeIf { it.startsWith(prefix) && it.endsWith(".avif") }?.removePrefix(prefix)?.removeSuffix(".avif")
            if (id == null || !WikiImageStorage.ID.matches(id) || storage.publicUrl(id) != url || repository.lock(id)?.status != WikiImageStatus.READY) {
                throw FailException("업로드가 완료된 이미지 자료실의 이미지만 사용할 수 있습니다.")
            }
        }
    }

    fun readyFile(id: String) = find(id).let {
        if (!properties.serveFiles || it.status != WikiImageStatus.READY) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        storage.publicFile(id)
    }

    fun readyThumbnailFile(id: String) = find(id).let {
        if (!properties.serveFiles || it.status != WikiImageStatus.READY || (it.thumbnailBytes ?: 0) <= 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        storage.thumbnailFile(id)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun thumbnailCandidates(urls: List<String>, lock: Boolean = false): List<WikiImageDto> {
        val prefix = "${properties.publicBaseUrl.trimEnd('/')}/wiki/"
        val ids = urls.mapNotNull { url ->
            val id = url.removePrefix(prefix).removeSuffix(".avif")
            id.takeIf { WikiImageStorage.ID.matches(it) && storage.publicUrl(it) == url }
        }.distinct()
        val records = ids.sorted().mapNotNull { if (lock) repository.lock(it) else repository.find(it) }
            .filter { it.status == WikiImageStatus.READY && (it.thumbnailBytes ?: 0) > 0 }.associateBy { it.id }
        return ids.mapNotNull { records[it]?.let(::dto) }
    }

    private fun find(id: String): WikiImage {
        if (!WikiImageStorage.ID.matches(id)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return repository.find(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    private fun dto(image: WikiImage) = WikiImageDto(
        id = image.id, status = image.status, description = image.description, sourceUrl = image.sourceUrl,
        createdAt = image.createdAt, url = if (image.status == WikiImageStatus.READY) storage.publicUrl(image.id) else null,
        width = image.width, height = image.height, frames = image.frames, bytes = image.bytes,
        animated = image.animated, error = image.error?.let(::errorMessage),
        revision = image.revision, updatedAt = image.updatedAt, updatedBy = image.updatedBy,
        thumbnailUrl = if (image.status == WikiImageStatus.READY && (image.thumbnailBytes ?: 0) > 0) storage.thumbnailUrl(image.id) else null,
    )

    companion object {
        private val PRIVATE_STATES = setOf(WikiImageStatus.DELETING, WikiImageStatus.DELETED, WikiImageStatus.RESTORING)

        private fun validateMetadata(description: String, source: String) {
            require(description.length <= 500 && description.none { it.isISOControl() && it !in "\n\t" }) { "이미지 설명은 500자 이내로 입력해 주세요." }
            require(source.length <= 1024 && validSource(source)) { "출처는 올바른 http 또는 https 주소로 입력해 주세요." }
        }

        fun validSource(source: String): Boolean = source.isEmpty() || runCatching {
            val uri = URI(source)
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)

        fun errorMessage(reason: String): String = when (reason) {
            "INVALID_IMAGE" -> "지원하지 않거나 손상된 이미지입니다."
            "LIMIT_EXCEEDED" -> "이미지의 해상도·프레임·용량 제한을 초과했습니다."
            "TIMEOUT" -> "이미지 변환 시간이 초과되었습니다. 더 작은 이미지로 다시 시도해 주세요."
            "UNAVAILABLE" -> "이미지 변환 서비스를 사용할 수 없습니다."
            "INTERRUPTED" -> "이미지 변환이 반복해서 중단되었습니다. 다시 업로드해 주세요."
            "EXPIRED" -> "이미지 처리 대기 시간이 초과되었습니다. 다시 업로드해 주세요."
            "ACCOUNT_UNAVAILABLE" -> "현재 계정으로 이미지 업로드를 완료할 수 없습니다."
            "STORAGE_FAILED" -> "이미지 저장에 실패했습니다. 잠시 후 다시 시도해 주세요."
            "FILE_OPERATION_FAILED" -> "이미지 삭제·복구를 완료하지 못했습니다. 파일은 보존되며 잠시 후 자동으로 다시 시도합니다."
            else -> "이미지 변환에 실패했습니다. 다른 이미지로 다시 시도해 주세요."
        }
    }
}
