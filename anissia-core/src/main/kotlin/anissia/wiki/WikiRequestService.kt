package anissia.wiki

import anissia.account.domain.Account
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageOperationRequest
import anissia.wiki.upload.WikiImageService
import anissia.wiki.upload.WikiImageStatus
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path
import java.time.OffsetDateTime

@Service
class WikiRequestService(
    private val access: WikiAccessService,
    private val requests: WikiRequestRepository,
    private val templates: WikiTemplateRepository,
    private val content: WikiContentService,
    private val images: WikiImageService,
) {
    @Transactional(readOnly = true)
    fun list(page: Int, status: WikiRequestStatus?, actor: Actor): WikiPage<WikiRequestDto> {
        val account = access.requireMember(actor)
        if (page !in 0..10000) throw FailException("잘못된 페이지입니다.")
        val result = requests.visibleRequests(account.an, account.isAdmin, status, PageRequest.of(page, 30))
        return WikiPage(result.content.map { dto(it, account, actor, includeBody = false) }, result.totalElements, result.number)
    }

    @Transactional(readOnly = true)
    fun get(id: Long, actor: Actor): WikiRequestDto {
        val account = access.requireMember(actor)
        return dto(visible(id, account), account, actor)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun submit(submission: WikiRequestSubmission, actor: Actor): WikiRequestDto {
        val account = access.requireContributor(actor)
        val request = WikiRequest(kind = submission.kind, reason = reason(submission.reason),
            requesterAn = account.an, createdBy = account.name)
        when (submission.kind) {
            WikiRequestKind.TEMPLATE_CREATE -> {
                val title = submission.title.trim()
                if (title.isBlank() || title.length > 100 || title.any(Char::isISOControl)) {
                    throw FailException("템플릿 이름은 1~100자로 입력해 주세요.")
                }
                if (templates.existsByTitleAndTemplateNoNot(title, 0)) throw FailException("같은 이름의 템플릿이 있습니다.")
                val body = submission.body ?: throw FailException("템플릿 본문을 입력해 주세요.")
                val refs = WikiBodyValidator.analyze(body, template = true)
                if (!refs.hasContent) throw FailException("템플릿 본문을 입력해 주세요.")
                if (refs.images.isNotEmpty()) throw FailException("템플릿 등록을 승인받은 뒤 해당 템플릿 안에서 이미지를 첨부해 주세요.")
                request.title = title
                request.body = Json.write(body)
            }
            WikiRequestKind.TEMPLATE_DELETE -> {
                val id = submission.targetNo ?: missing()
                val template = templates.lockTemplate(id)?.takeUnless { it.deleted } ?: missing()
                checkRevision(submission.baseRevision, template.revision)
                request.targetNo = id
                request.baseRevision = template.revision
                request.title = template.title
            }
            WikiRequestKind.IMAGE_DELETE -> {
                val id = submission.imageId ?: missing()
                val image = images.get(id, actor)
                if (image.status != WikiImageStatus.READY) throw FailException("공개된 이미지만 삭제 요청할 수 있습니다.")
                checkRevision(submission.baseRevision, image.revision)
                request.imageId = id
                request.baseRevision = image.revision
                request.title = image.description.ifBlank { "이미지 삭제 요청" }.take(100)
                request.description = image.description
                request.sourceUrl = image.sourceUrl
            }
            WikiRequestKind.IMAGE_CREATE -> throw FailException("이미지 파일을 첨부하여 등록 요청해 주세요.")
        }
        return dto(requests.saveAndFlush(request), account, actor)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun submitImage(file: MultipartFile, description: String, sourceUrl: String, reason: String, actor: Actor,
                    documentKind: String? = null, documentNo: Long? = null): WikiRequestDto {
        val account = access.requireImageUploadRequest(actor)
        val requestReason = this.reason(reason)
        if (description.isBlank() || sourceUrl.isBlank()) throw FailException("이미지 설명과 출처를 입력해 주세요.")
        val image = images.receiveRequest(file, description, sourceUrl, actor, documentKind, documentNo)
        val request = WikiRequest(kind = WikiRequestKind.IMAGE_CREATE, title = description.trim().take(100),
            reason = requestReason, imageId = image.id, description = description.trim(), sourceUrl = sourceUrl.trim(),
            requesterAn = account.an, createdBy = account.name)
        return dto(requests.saveAndFlush(request), account, actor)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun approve(id: Long, decision: WikiRequestDecision, actor: Actor): WikiRequestDto {
        val account = access.requireOperator(actor)
        val request = requests.lockRequest(id) ?: missing()
        requirePending(request)
        val decisionReason = reason(decision.reason)
        when (request.kind) {
            WikiRequestKind.TEMPLATE_CREATE -> {
                val result = content.createTemplate(WikiEditRequest(body = Json.read(requireNotNull(request.body)),
                    title = request.title, summary = "문서 요청 #${request.id} 승인"), actor)
                request.resultTargetNo = result.templateNo
            }
            WikiRequestKind.TEMPLATE_DELETE -> {
                content.deleteTemplate(requireNotNull(request.targetNo), requireNotNull(request.baseRevision), actor)
                request.resultTargetNo = request.targetNo
            }
            WikiRequestKind.IMAGE_CREATE -> {
                val image = images.approveRequest(requireNotNull(request.imageId), actor)
                request.resultImageId = image.id
            }
            WikiRequestKind.IMAGE_DELETE -> {
                val image = images.delete(requireNotNull(request.imageId),
                    WikiImageOperationRequest(requireNotNull(request.baseRevision)), actor)
                request.resultImageId = image.id
            }
        }
        complete(request, WikiRequestStatus.APPROVED, decisionReason, account)
        return dto(request, account, actor)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun reject(id: Long, decision: WikiRequestDecision, actor: Actor): WikiRequestDto {
        val account = access.requireOperator(actor)
        val request = requests.lockRequest(id) ?: missing()
        requirePending(request)
        val decisionReason = reason(decision.reason)
        if (request.kind == WikiRequestKind.IMAGE_CREATE) images.discardRequest(requireNotNull(request.imageId), actor)
        complete(request, WikiRequestStatus.REJECTED, decisionReason, account)
        return dto(request, account, actor)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun cancel(id: Long, actor: Actor): WikiRequestDto {
        val account = access.requireMember(actor)
        val request = requests.lockRequest(id)?.takeIf { it.requesterAn == account.an } ?: missing()
        requirePending(request)
        if (request.kind == WikiRequestKind.IMAGE_CREATE) images.discardRequest(requireNotNull(request.imageId), actor)
        complete(request, WikiRequestStatus.CANCELLED, "작성자가 요청을 취소했습니다.", account)
        return dto(request, account, actor)
    }

    @Transactional(readOnly = true)
    fun imageFile(id: Long, actor: Actor): Path {
        val account = access.requireMember(actor)
        val request = visible(id, account)
        if (request.kind != WikiRequestKind.IMAGE_CREATE) missing()
        return images.requestFile(requireNotNull(request.imageId), actor)
    }

    private fun visible(id: Long, account: Account): WikiRequest = requests.findByIdOrNull(id)
        ?.takeIf { it.requesterAn == account.an || account.isAdmin } ?: missing()

    private fun complete(request: WikiRequest, status: WikiRequestStatus, reason: String, account: Account) {
        request.status = status
        request.processedAn = account.an
        request.processedBy = account.name
        request.processedAt = OffsetDateTime.now()
        request.processingReason = reason
    }

    private fun dto(request: WikiRequest, account: Account, actor: Actor, includeBody: Boolean = true): WikiRequestDto {
        val image = if (request.kind == WikiRequestKind.IMAGE_CREATE) request.imageId?.let { images.requestImage(it, actor) } else null
        val pending = request.status == WikiRequestStatus.PENDING
        val canProcess = pending && account.isAdmin
        val ready = image?.status == WikiImageStatus.REVIEW_READY
        return WikiRequestDto(request.id, request.kind, request.status, request.title, request.reason,
            request.createdBy, request.createdAt, request.processedBy, request.processedAt, request.processingReason,
            request.targetNo, request.imageId, request.body?.takeIf { includeBody }?.let { Json.read<WikiBody>(it) },
            request.description, request.sourceUrl,
            if (request.kind == WikiRequestKind.IMAGE_CREATE && ready) "/wiki/requests/${request.id}/image" else null,
            request.resultTargetNo, request.resultImageId, image, pending && request.requesterAn == account.an,
            canProcess, canProcess && (request.kind != WikiRequestKind.IMAGE_CREATE || ready))
    }

    private fun requirePending(request: WikiRequest) {
        if (request.status != WikiRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 요청입니다. 최신 상태를 확인해 주세요.")
        }
    }

    private fun checkRevision(expected: Long?, actual: Long) {
        if (expected == null || expected < 0 || expected != actual) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "대상 내용이 변경되었습니다. 최신 내용을 확인한 뒤 다시 요청해 주세요.")
        }
    }

    private fun reason(raw: String): String = raw.trim().also {
        if (it.isBlank() || it.length > 2000 || it.any { char -> char.isISOControl() && char !in "\n\r\t" }) {
            throw FailException("요청·처리 사유를 1~2000자로 입력해 주세요.")
        }
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 문서 요청입니다.")
}
