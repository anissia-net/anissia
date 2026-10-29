package anissia.wiki

import anissia.account.domain.Account
import anissia.anime.domain.Anime
import anissia.anime.repository.AnimeRepository
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageService
import anissia.wiki.upload.WikiImageDto
import anissia.wiki.upload.WikiImageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional

class WikiContentServiceTest {
    private val access = mock(WikiAccessService::class.java)
    private val anime = mock(AnimeRepository::class.java)
    private val documents = mock(AnimeWikiRepository::class.java)
    private val templates = mock(WikiTemplateRepository::class.java)
    private val revisions = mock(WikiRevisionRepository::class.java)
    private val references = mock(WikiReferenceRepository::class.java)
    private val images = mock(WikiImageService::class.java)
    private val service = WikiContentService(access, anime, documents, templates, revisions, references, images, org.mockito.Mockito.mock(anissia.search.ContentSearchRepository::class.java), org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java))
    private val actor = Actor(an = 7)
    private val account = Account(an = 7, name = "기여자")
    private val source = Anime(animeNo = 11, subject = "현재 애니 제목")
    private val textBody = WikiBody(blocks = Json.readTree("[{\"w\":\"p\",\"ch\":[\"애니 소개\"]}]"))

    private fun allow() {
        `when`(access.requireContributor(actor)).thenReturn(account)
        `when`(access.requireOperator(actor)).thenReturn(account)
        `when`(documents.lockAnime(11)).thenReturn(source)
    }

    @Test
    fun `an absent anime cannot create a detached wiki`() {
        `when`(access.requireContributor(actor)).thenReturn(account)
        val error = assertThrows<ResponseStatusException> {
            service.editAnime(11, WikiEditRequest(baseRevision = 0, body = WikiBody.empty()), actor)
        }
        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
        verifyNoInteractions(revisions, references, templates, images)
    }

    @Test
    fun `stale edits are rejected before changing current body history or references`() {
        allow()
        val current = AnimeWiki(11).apply { revision = 3; body = Json.write(WikiBody.empty()) }
        `when`(documents.lockWiki(11)).thenReturn(current)
        val error = assertThrows<ResponseStatusException> {
            service.editAnime(11, WikiEditRequest(baseRevision = 2, body = WikiBody.empty()), actor)
        }
        assertEquals(HttpStatus.CONFLICT, error.statusCode)
        assertEquals(3L, current.revision)
        verifyNoInteractions(revisions, references, templates, images)
    }

    @Test
    fun `first edit locks parent before current wiki and uses the anime title`() {
        allow()
        val result = service.editAnime(11, WikiEditRequest(0, textBody, "첫 작성", "임의 문서 제목"), actor)
        assertEquals(11L, result.animeNo)
        assertEquals("현재 애니 제목", result.title)
        assertEquals(1L, result.revision)
        val captured = ArgumentCaptor.forClass(WikiRevision::class.java)
        verify(revisions).save(captured.capture())
        assertEquals("현재 애니 제목", captured.value.title)
        assertEquals(7L, captured.value.authorNo)
        inOrder(documents).run {
            verify(documents).lockAnime(11)
            verify(documents).lockWiki(11)
        }
    }

    @Test
    fun `anime deletion locks the parent and removes revisions and references before the document`() {
        allow()
        val current = AnimeWiki(11)
        `when`(documents.lockWiki(11)).thenReturn(current)
        service.deleteForAnime(11)
        inOrder(documents, references, revisions).run {
            verify(documents).lockAnime(11)
            verify(references).deleteAllByOwnerKindAndOwnerNo("anime", 11)
            verify(revisions).deleteAllByKindAndTargetNo("anime", 11)
            verify(documents).lockWiki(11)
            verify(documents).delete(current)
            verify(documents).flush()
        }
        verifyNoInteractions(templates, images)
    }

    @Test
    fun `template deletion checks current locked usage and cannot remove a referenced template`() {
        allow()
        val template = WikiTemplate(templateNo = 5, title = "공통 정보").apply { revision = 2; body = Json.write(WikiBody.empty()) }
        `when`(templates.lockTemplate(5)).thenReturn(template)
        `when`(references.findFirstByTargetKindAndTargetId("template", "5")).thenReturn(WikiReference(targetKind = "template", targetId = "5"))
        assertThrows<FailException> { service.deleteTemplate(5, 2, actor) }
        assertFalse(template.deleted)
        assertEquals(2L, template.revision)
        verifyNoInteractions(revisions, images)
    }

    @Test
    fun `template rename collision never exposes the database error`() {
        allow()
        val template = WikiTemplate(templateNo = 5, title = "기존 이름").apply { revision = 2; body = Json.write(WikiBody.empty()) }
        `when`(templates.lockTemplate(5)).thenReturn(template)
        org.mockito.Mockito.doThrow(DataIntegrityViolationException("private sql details")).`when`(templates).flush()
        val error = assertThrows<FailException> { service.editTemplate(5, WikiEditRequest(2, WikiBody.empty(), title = "경합 이름"), actor) }
        assertEquals("같은 이름의 템플릿이 있습니다.", error.message)
        verifyNoInteractions(revisions, images, references)
    }

    @Test
    fun `repeated template inclusion counts toward the expanded byte budget`() {
        allow()
        val templateBody = WikiBody(blocks = Json.readTree("[{\"w\":\"p\",\"ch\":[\"${"x".repeat(330_000)}\"]}]"))
        val template = WikiTemplate(templateNo = 5, title = "큰 템플릿").apply { revision = 1; body = Json.write(templateBody) }
        `when`(templates.lockTemplate(5)).thenReturn(template)
        val body = WikiBody(blocks = Json.readTree("[${List(15) { "{\"w\":\"exTemplateInclude\",\"a\":{\"exTemplateNo\":\"5\"},\"ch\":[]}" }.joinToString(",")}]"))
        val error = assertThrows<FailException> { service.editAnime(11, WikiEditRequest(0, body), actor) }
        assertEquals("템플릿을 포함한 위키 본문은 4MB 이내로 작성해 주세요.", error.message)
        verifyNoInteractions(revisions, references)
    }

    @Test
    fun `edits cannot empty a document or change its history and references`() {
        allow()
        val current = AnimeWiki(11).apply { revision = 1; body = Json.write(textBody) }
        `when`(documents.lockWiki(11)).thenReturn(current)
        assertThrows<FailException> { service.editAnime(11, WikiEditRequest(1, WikiBody.empty()), actor) }
        assertEquals(1L, current.revision)
        assertEquals(Json.write(textBody), current.body)
        verifyNoInteractions(revisions, references, images)
    }

    @Test
    fun `first image in expanded document order becomes thumbnail only while absent`() {
        allow()
        val included = image("b")
        val direct = image("a")
        val templateBody = imageBody(included.url!!)
        val template = WikiTemplate(templateNo = 5, title = "사진").apply { revision = 1; body = Json.write(templateBody) }
        `when`(templates.lockTemplate(5)).thenReturn(template)
        `when`(templates.findAllById(setOf(5L))).thenReturn(listOf(template))
        val body = WikiBody(blocks = Json.readTree("""[{"w":"exTemplateInclude","a":{"exTemplateNo":"5"}},{"w":"img","a":{"src":"${direct.url}"}}]"""))
        `when`(images.thumbnailCandidates(listOf(included.url, direct.url!!), true)).thenReturn(listOf(included, direct))

        service.editAnime(11, WikiEditRequest(0, body), actor)
        assertEquals(included.thumbnailUrl, source.thumbnail)

        service.editAnime(11, WikiEditRequest(0, imageBody(direct.url)), actor)
        assertEquals(included.thumbnailUrl, source.thumbnail)
    }

    @Test
    fun `text only wiki leaves thumbnail empty`() {
        allow()
        service.editAnime(11, WikiEditRequest(0, textBody), actor)
        assertNull(source.thumbnail)
    }

    @Test
    fun `thumbnail selection requires operator before reading wiki`() {
        `when`(access.requireOperator(actor)).thenThrow(FailException("운영자만 가능"))
        assertThrows<FailException> { service.selectAnimeThumbnail(11, WikiThumbnailRequest("a"), actor) }
        assertThrows<FailException> { service.animeImages(11, actor) }
        verifyNoInteractions(anime, documents, templates, images)
    }

    @Test
    fun `operator may choose only a current document image and may clear it`() {
        allow()
        val image = image("a")
        val wiki = AnimeWiki(11).apply { revision = 1; body = Json.write(imageBody(image.url!!)) }
        `when`(documents.lockWiki(11)).thenReturn(wiki)
        `when`(images.thumbnailCandidates(listOf(image.url!!), true)).thenReturn(listOf(image))
        assertThrows<FailException> { service.selectAnimeThumbnail(11, WikiThumbnailRequest("b".repeat(32)), actor) }
        assertNull(source.thumbnail)
        assertEquals(image.thumbnailUrl, service.selectAnimeThumbnail(11, WikiThumbnailRequest(image.id), actor).thumbnail)
        assertEquals(image.thumbnailUrl, source.thumbnail)
        assertNull(service.selectAnimeThumbnail(11, WikiThumbnailRequest(null), actor).thumbnail)
        assertNull(source.thumbnail)
    }

    @Test
    fun `image picker returns existing thumbnail even when no longer in document`() {
        allow()
        val current = image("a")
        source.thumbnail = current.thumbnailUrl
        `when`(anime.findById(11)).thenReturn(Optional.of(source))
        `when`(documents.findById(11)).thenReturn(Optional.of(AnimeWiki(11).apply { body = Json.write(textBody) }))
        val result = service.animeImages(11, actor)
        assertEquals(current.thumbnailUrl, result.thumbnail)
        assertEquals(emptyList<WikiImageDto>(), result.images)
    }

    @Test
    fun `image traversal expands templates in place and removes duplicates`() {
        val body = WikiBody(blocks = Json.readTree("""[{"w":"quote","ch":[{"w":"img","a":{"src":"first"}},{"w":"exTemplateInclude","a":{"exTemplateNo":"5"}},{"w":"img","a":{"src":"last"}}]},{"w":"img","a":{"src":"first"}}]"""))
        assertEquals(listOf("first", "included", "last"), WikiBodyValidator.imageUrls(body, mapOf(5L to imageBody("included"))))
    }

    private fun image(letter: String): WikiImageDto {
        val id = letter.repeat(32)
        return WikiImageDto(id, WikiImageStatus.READY, "사진", "", Instant.EPOCH,
            url = "https://file.example/wiki/$id.avif", thumbnailUrl = "https://file.example/wiki/$id.thumb.avif")
    }

    private fun imageBody(url: String) = WikiBody(blocks = Json.readTree("""[{"w":"img","a":{"src":"$url"}}]"""))
}
