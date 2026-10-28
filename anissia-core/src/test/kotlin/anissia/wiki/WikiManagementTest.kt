package anissia.wiki

import anissia.account.domain.Account
import anissia.anime.domain.Anime
import anissia.anime.repository.AnimeRepository
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class WikiManagementTest {
    private val access = mock(WikiAccessService::class.java)
    private val anime = mock(AnimeRepository::class.java)
    private val documents = mock(AnimeWikiRepository::class.java)
    private val templates = mock(WikiTemplateRepository::class.java)
    private val revisions = mock(WikiRevisionRepository::class.java)
    private val references = mock(WikiReferenceRepository::class.java)
    private val images = mock(WikiImageService::class.java)
    private val service = WikiContentService(access, anime, documents, templates, revisions, references, images)
    private val actor = Actor(an = 7)
    private val body = WikiBody(blocks = Json.readTree("[{\"w\":\"p\",\"ch\":[\"정보\"]}]"))
    private fun deleted() = WikiTemplate(templateNo = 5, title = "공통 정보", deleted = true).apply { revision = 3; this.body = Json.write(this@WikiManagementTest.body) }

    @Test
    fun `only current operators can view or recover a deleted template`() {
        val template = deleted()
        `when`(templates.findById(5)).thenReturn(Optional.of(template))
        `when`(access.access(actor)).thenReturn(WikiAccess(false, false, null, ""))
        assertEquals(HttpStatus.NOT_FOUND, assertThrows<ResponseStatusException> { service.templateDocument(5, actor) }.statusCode)
        `when`(access.requireOperator(actor)).thenThrow(FailException("운영자 권한이 필요합니다."))
        assertThrows<FailException> { service.recoverTemplate(5, 3, actor) }
        verifyNoInteractions(revisions, images, references)
        assertTrue(template.deleted)
    }

    @Test
    fun `recover creates a new revision and restores current references`() {
        val template = deleted()
        `when`(templates.lockTemplate(5)).thenReturn(template)
        `when`(access.requireOperator(actor)).thenReturn(Account(an = 7, name = "운영자"))
        val result = service.recoverTemplate(5, 3, actor)
        assertFalse(result.deleted)
        assertEquals(4L, result.revision)
        verify(images).requireReadyUrls(emptySet())
        verify(revisions).save(org.mockito.ArgumentMatchers.argThat { it.revision == 4L && it.summary.contains("복구") })
        verify(references).deleteAllByOwnerKindAndOwnerNo("template", 5)
    }

    @Test
    fun `stale recover cannot create duplicate restoration revisions`() {
        val template = deleted()
        `when`(templates.lockTemplate(5)).thenReturn(template)
        `when`(access.requireOperator(actor)).thenReturn(Account(an = 7))
        assertEquals(HttpStatus.CONFLICT, assertThrows<ResponseStatusException> { service.recoverTemplate(5, 2, actor) }.statusCode)
        assertEquals(3L, template.revision)
        verifyNoInteractions(revisions, references, images)
    }

    @Test
    fun `past revisions remain readable without expanding current templates`() {
        `when`(anime.findById(11)).thenReturn(Optional.of(Anime(animeNo = 11, subject = "바뀐 작품명")))
        val saved = WikiRevision(kind = "anime", targetNo = 11, revision = 2, title = "옛 이름", body = Json.write(body), templates = "[]")
        `when`(revisions.findByKindAndTargetNoAndRevision("anime", 11, 2)).thenReturn(saved)
        val result = service.revision("anime", 11, 2)
        assertEquals("바뀐 작품명", result.title)
        assertEquals(body, result.body)
        verifyNoInteractions(documents, templates, images)
    }

    @Test
    fun `large live template cannot hide the document revision and recovery controls`() {
        `when`(anime.findById(11)).thenReturn(Optional.of(Anime(animeNo = 11, subject = "작품")))
        val references = WikiBody(blocks = Json.readTree("[${List(20) { "{\"w\":\"exTemplateInclude\",\"a\":{\"exTemplateNo\":\"5\"},\"ch\":[]}" }.joinToString(",")}]"))
        `when`(documents.findById(11)).thenReturn(Optional.of(AnimeWiki(11).apply { revision = 4; body = Json.write(references) }))
        val template = WikiTemplate(templateNo = 5, title = "큰 틀").apply { revision = 2; body = Json.write(WikiBody(blocks = Json.readTree("[{\"w\":\"p\",\"ch\":[\"${"a".repeat(300_000)}\"]}]"))) }
        `when`(templates.findAllById(setOf(5L))).thenReturn(listOf(template))
        val result = service.animeDocument(11)
        assertEquals(4L, result.revision)
        assertEquals(references, result.body)
        assertNotNull(result.renderWarning)
        assertTrue(result.templates.isEmpty())
    }
}
