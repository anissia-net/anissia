package anissia.wiki

import anissia.account.domain.Account
import anissia.anime.repository.AnimeRepository
import anissia.search.ContentSearchRepository
import anissia.search.SearchKind
import anissia.search.SearchChangedEvent
import org.springframework.context.ApplicationEventPublisher
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageService
import anissia.wiki.upload.WikiImageDto
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

data class WikiTemplateSnapshot(val templateNo: Long, val title: String, val body: WikiBody, val revision: Long)
data class WikiDocument(
    val animeNo: Long? = null,
    val templateNo: Long? = null,
    val title: String,
    val body: WikiBody,
    val revision: Long,
    val updatedAt: OffsetDateTime? = null,
    val updatedBy: String? = null,
    val templates: List<WikiTemplateSnapshot> = emptyList(),
    val deleted: Boolean = false,
    val renderWarning: String? = null,
)
data class WikiEditRequest(val baseRevision: Long = -1, val body: WikiBody = WikiBody(), val summary: String = "", val title: String = "")
data class WikiRestoreRequest(val baseRevision: Long = -1, val summary: String = "")
data class WikiHistoryItem(val revision: Long, val summary: String, val updatedBy: String, val updatedAt: OffsetDateTime)
data class WikiPage<T>(val content: List<T>, val totalElements: Long, val page: Int, val size: Int = 30)
data class WikiRecentItem(val kind: String, val targetNo: Long, val title: String, val revision: Long,
    val summary: String, val updatedBy: String, val updatedAt: OffsetDateTime, val deleted: Boolean,
    val thumbnail: String? = null)
data class WikiUsageItem(val kind: String, val targetNo: Long, val title: String)
data class WikiAnimeImages(val thumbnail: String?, val images: List<WikiImageDto>)
data class WikiThumbnailRequest(val imageId: String? = null)

@Service
class WikiContentService(
    private val access: WikiAccessService,
    private val anime: AnimeRepository,
    private val documents: AnimeWikiRepository,
    private val templates: WikiTemplateRepository,
    private val revisions: WikiRevisionRepository,
    private val references: WikiReferenceRepository,
    private val images: WikiImageService,
    private val search: ContentSearchRepository,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun animeDocument(animeNo: Long): WikiDocument {
        val source = anime.findByIdOrNull(animeNo) ?: missing()
        val wiki = documents.findByIdOrNull(animeNo)
            ?: return WikiDocument(animeNo = animeNo, title = source.subject, body = WikiBody.empty(), revision = 0)
        return document("anime", animeNo, source.subject, wiki)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun editAnime(animeNo: Long, request: WikiEditRequest, actor: Actor): WikiDocument {
        val account = access.requireContributor(actor)
        val source = documents.lockAnime(animeNo) ?: missing()
        val wiki = documents.lockWiki(animeNo) ?: AnimeWiki(animeNo)
        checkRevision(request.baseRevision, wiki.revision)
        val snapshots = save("anime", animeNo, source.subject, wiki, request.body, request.summary, account)
        if (source.thumbnail.isNullOrBlank()) {
            source.thumbnail = imageCandidates(request.body, snapshots, lock = true).firstOrNull()?.thumbnailUrl
        }
        documents.save(wiki)
        return document("anime", animeNo, source.subject, wiki)
    }

    @Transactional(readOnly = true)
    fun animeImages(animeNo: Long, actor: Actor): WikiAnimeImages {
        access.requireOperator(actor)
        val source = anime.findByIdOrNull(animeNo) ?: missing()
        val wiki = documents.findByIdOrNull(animeNo)
            ?: return WikiAnimeImages(source.thumbnail, emptyList())
        val document = document("anime", animeNo, source.subject, wiki)
        return WikiAnimeImages(source.thumbnail, imageCandidates(document.body, document.templates))
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun selectAnimeThumbnail(animeNo: Long, request: WikiThumbnailRequest, actor: Actor): WikiAnimeImages {
        access.requireOperator(actor)
        val source = documents.lockAnime(animeNo) ?: missing()
        val wiki = documents.lockWiki(animeNo)
        val candidates = if (wiki == null) emptyList() else {
            val body = Json.read<WikiBody>(wiki.body)
            WikiBodyValidator.analyze(body).templates.sorted().forEach { templates.lockTemplate(it) }
            val document = document("anime", animeNo, source.subject, wiki)
            imageCandidates(document.body, document.templates, lock = true)
        }
        source.thumbnail = request.imageId?.let { id ->
            candidates.firstOrNull { it.id == id }?.thumbnailUrl
                ?: throw FailException("현재 위키 문서에서 사용하는 이미지 중에서 선택해 주세요.")
        }
        return WikiAnimeImages(source.thumbnail, candidates)
    }

    private fun imageCandidates(body: WikiBody, snapshots: List<WikiTemplateSnapshot>, lock: Boolean = false): List<WikiImageDto> =
        images.thumbnailCandidates(WikiBodyValidator.imageUrls(body, snapshots.associate { it.templateNo to it.body }), lock)

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun deleteForAnime(animeNo: Long) {
        documents.lockAnime(animeNo) ?: return
        references.deleteAllByOwnerKindAndOwnerNo("anime", animeNo)
        revisions.deleteAllByKindAndTargetNo("anime", animeNo)
        documents.lockWiki(animeNo)?.let { documents.delete(it) }
        documents.flush()
    }

    @Transactional(readOnly = true)
    fun templateList(q: String, page: Int, deleted: Boolean = false, actor: Actor = Actor()): WikiPage<WikiDocument> {
        if (deleted) access.requireOperator(actor)
        if (q.isNotBlank()) {
            val found = search.search(SearchKind.TEMPLATE, q, page, 30, mapOf("status" to if (deleted) "DELETED" else "READY"))
            val byId = templates.findAllById(found.ids.map(String::toLong)).filter { it.deleted == deleted }.associateBy { it.templateNo.toString() }
            return WikiPage(found.ids.mapNotNull { byId[it]?.let { template -> document("template", template.templateNo, template.title, template) } }, found.total, page)
        }
        val result = templates.findAllByDeletedAndTitleContainingOrderByTitle(deleted, "", PageRequest.of(page.coerceIn(0, 10000), 30))
        return WikiPage(result.content.map { document("template", it.templateNo, it.title, it) }, result.totalElements, result.number)
    }

    @Transactional(readOnly = true)
    fun templateDocument(templateNo: Long, actor: Actor = Actor()): WikiDocument {
        val template = visibleTemplate(templateNo, actor)
        return document("template", templateNo, template.title, template)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun createTemplate(request: WikiEditRequest, actor: Actor): WikiDocument {
        val account = access.requireContributor(actor)
        val title = title(request.title)
        if (templates.existsByTitleAndTemplateNoNot(title, 0)) throw FailException("같은 이름의 템플릿이 있습니다.")
        val template = try {
            templates.saveAndFlush(WikiTemplate(title = title))
        } catch (_: DataIntegrityViolationException) {
            throw FailException("같은 이름의 템플릿이 있습니다.")
        }
        save("template", template.templateNo, title, template, request.body, request.summary, account)
        return document("template", template.templateNo, title, template)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun editTemplate(templateNo: Long, request: WikiEditRequest, actor: Actor): WikiDocument {
        val account = access.requireContributor(actor)
        val template = templates.lockTemplate(templateNo)?.takeUnless { it.deleted } ?: missing()
        checkRevision(request.baseRevision, template.revision)
        val title = title(request.title)
        if (templates.existsByTitleAndTemplateNoNot(title, templateNo)) throw FailException("같은 이름의 템플릿이 있습니다.")
        template.title = title
        try {
            templates.flush()
        } catch (_: DataIntegrityViolationException) {
            throw FailException("같은 이름의 템플릿이 있습니다.")
        }
        save("template", templateNo, title, template, request.body, request.summary, account)
        return document("template", templateNo, title, template)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun deleteTemplate(templateNo: Long, baseRevision: Long, actor: Actor) {
        val account = access.requireOperator(actor)
        val template = templates.lockTemplate(templateNo)?.takeUnless { it.deleted } ?: missing()
        checkRevision(baseRevision, template.revision)
        if (references.findFirstByTargetKindAndTargetId("template", templateNo.toString()) != null) {
            throw FailException("다른 위키에서 사용 중인 템플릿은 삭제할 수 없습니다.")
        }
        save("template", templateNo, template.title, template, Json.read(template.body), "운영자에 의한 템플릿 삭제", account)
        template.deleted = true
        references.deleteAllByOwnerKindAndOwnerNo("template", templateNo)
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun recoverTemplate(templateNo: Long, baseRevision: Long, actor: Actor): WikiDocument {
        val account = access.requireOperator(actor)
        val template = templates.lockTemplate(templateNo) ?: missing()
        checkRevision(baseRevision, template.revision)
        if (!template.deleted) throw FailException("이미 공개된 템플릿입니다.")
        save("template", templateNo, template.title, template, Json.read(template.body), "운영자에 의한 템플릿 복구", account)
        template.deleted = false
        return document("template", templateNo, template.title, template)
    }

    @Transactional(readOnly = true)
    fun recent(page: Int, actor: Actor): WikiPage<WikiRecentItem> {
        val result = revisions.recent(access.access(actor).isOperator, PageRequest.of(page.coerceIn(0, 10000), 30))
        return WikiPage(result.content, result.totalElements, result.number)
    }

    @Transactional(readOnly = true)
    fun templateUsage(templateNo: Long, page: Int, actor: Actor): WikiPage<WikiUsageItem> {
        visibleTemplate(templateNo, actor)
        val result = references.templateUsage(templateNo.toString(), PageRequest.of(page.coerceIn(0, 10000), 30))
        return WikiPage(result.content, result.totalElements, result.number)
    }

    @Transactional(readOnly = true)
    fun history(kind: String, targetNo: Long, page: Int, actor: Actor = Actor()): List<WikiHistoryItem> {
        targetTitle(kind, targetNo, actor)
        return revisions.findAllByKindAndTargetNoOrderByRevisionDesc(kind, targetNo, PageRequest.of(page.coerceIn(0, 10000), 50))
            .map { WikiHistoryItem(it.revision, it.summary, it.updatedBy, it.updatedAt) }
    }

    @Transactional(readOnly = true)
    fun revision(kind: String, targetNo: Long, revision: Long, actor: Actor = Actor()): WikiDocument {
        val currentTitle = targetTitle(kind, targetNo, actor)
        val entry = revisions.findByKindAndTargetNoAndRevision(kind, targetNo, revision) ?: missing()
        return WikiDocument(
            animeNo = targetNo.takeIf { kind == "anime" }, templateNo = targetNo.takeIf { kind == "template" },
            title = if (kind == "anime") currentTitle else entry.title,
            body = Json.read(entry.body), revision = entry.revision, updatedAt = entry.updatedAt, updatedBy = entry.updatedBy,
            templates = Json.read(entry.templates),
        )
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun restore(kind: String, targetNo: Long, revision: Long, request: WikiRestoreRequest, actor: Actor): WikiDocument {
        access.requireContributor(actor)
        val entry = revisions.findByKindAndTargetNoAndRevision(kind, targetNo, revision) ?: missing()
        val edit = WikiEditRequest(request.baseRevision, Json.read(entry.body), request.summary.ifBlank { "${revision}판으로 되돌림" }, entry.title)
        return when (kind) {
            "anime" -> editAnime(targetNo, edit, actor)
            "template" -> editTemplate(targetNo, edit, actor)
            else -> missing()
        }
    }

    private fun targetTitle(kind: String, id: Long, actor: Actor): String = when (kind) {
        "anime" -> anime.findByIdOrNull(id)?.subject ?: missing()
        "template" -> visibleTemplate(id, actor).title
        else -> missing()
    }

    private fun visibleTemplate(id: Long, actor: Actor): WikiTemplate {
        val template = templates.findByIdOrNull(id) ?: missing()
        if (template.deleted && !access.access(actor).isOperator) missing()
        return template
    }

    private fun save(kind: String, id: Long, title: String, state: WikiState, body: WikiBody, summary: String, account: Account): List<WikiTemplateSnapshot> {
        if (summary.length > 300 || summary.any { it.isISOControl() }) throw FailException("수정 요약은 300자 이내로 입력해 주세요.")
        val refs = WikiBodyValidator.analyze(body, kind == "template")
        if (!refs.hasContent) throw FailException("위키 본문을 입력해 주세요.")
        val json = Json.write(body)
        var expandedBytes = json.toByteArray(Charsets.UTF_8).size.toLong()
        val snapshots = refs.templates.sorted().map { templateNo ->
            val template = templates.lockTemplate(templateNo)?.takeUnless { it.deleted }
                ?: throw FailException("존재하지 않거나 삭제된 템플릿입니다.")
            WikiTemplateSnapshot(templateNo, template.title, Json.read(template.body), template.revision).also {
                expandedBytes += Json.write(it).toByteArray(Charsets.UTF_8).size.toLong() * refs.templateUses.getValue(templateNo)
                if (expandedBytes > 4L * 1024 * 1024) throw FailException("템플릿을 포함한 위키 본문은 4MB 이내로 작성해 주세요.")
            }
        }
        images.requireReadyUrls(WikiBodyValidator.imageUrls(body, snapshots.associate { it.templateNo to it.body }).toSet())
        if (state.revision > 0 && state.body == json && kind == "anime") throw FailException("변경사항이 없습니다.")
        state.body = json
        state.revision++
        state.updatedAt = OffsetDateTime.now()
        state.updatedBy = account.name
        revisions.save(WikiRevision(kind = kind, targetNo = id, revision = state.revision, title = title,
            body = json, templates = Json.write(snapshots), summary = summary.trim(), authorNo = account.an,
            updatedBy = account.name, updatedAt = state.updatedAt))
        references.deleteAllByOwnerKindAndOwnerNo(kind, id)
        references.saveAll(refs.templates.map { WikiReference(ownerKind = kind, ownerNo = id, targetKind = "template", targetId = it.toString()) } +
            refs.images.map { WikiReference(ownerKind = kind, ownerNo = id, targetKind = "image", targetId = it) })
        events.publishEvent(SearchChangedEvent(if (kind == "anime") SearchKind.ANIME else SearchKind.TEMPLATE, id))
        return snapshots
    }

    private fun document(kind: String, id: Long, title: String, state: WikiState): WikiDocument {
        val body: WikiBody = Json.read(state.body)
        val refs = if (kind == "anime") WikiBodyValidator.analyze(body) else WikiReferences(emptySet(), emptySet())
        var expandedBytes = state.body.toByteArray(Charsets.UTF_8).size.toLong()
        var warning: String? = null
        val snapshots = templates.findAllById(refs.templates).filterNot { it.deleted }.sortedBy { it.templateNo }
            .mapNotNull {
                val snapshot = WikiTemplateSnapshot(it.templateNo, it.title, Json.read(it.body), it.revision)
                val size = Json.write(snapshot).toByteArray(Charsets.UTF_8).size.toLong() * refs.templateUses.getValue(it.templateNo)
                if (expandedBytes + size > 4L * 1024 * 1024) {
                    warning = "일부 템플릿의 분량이 너무 커 표시하지 못했습니다. 본문 수정과 변경 이력 확인은 가능합니다."
                    null
                } else {
                    expandedBytes += size
                    snapshot
                }
            }
        return WikiDocument(id.takeIf { kind == "anime" }, id.takeIf { kind == "template" }, title, body,
            state.revision, state.updatedAt, state.updatedBy, snapshots, (state as? WikiTemplate)?.deleted == true, warning)
    }

    private fun title(raw: String): String = raw.trim().also {
        if (it.isBlank() || it.length > 100 || it.any(Char::isISOControl)) throw FailException("템플릿 이름은 1~100자로 입력해 주세요.")
    }

    private fun checkRevision(base: Long, current: Long) {
        if (base < 0 || base != current) throw ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 먼저 수정했습니다. 작성 중인 내용을 보관한 후 최신 판을 확인해 주세요.")
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않거나 삭제된 위키입니다.")
}
