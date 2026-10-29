package anissia.search

import anissia.account.domain.Account
import anissia.anime.domain.Anime
import anissia.anime.repository.AnimeRepository
import anissia.security.Actor
import anissia.support.Json
import anissia.wiki.*
import anissia.wiki.upload.WikiImageService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher

class SearchMutationEventsTest {
    private val access = mock(WikiAccessService::class.java)
    private val documents = mock(AnimeWikiRepository::class.java)
    private val templates = mock(WikiTemplateRepository::class.java)
    private val revisions = mock(WikiRevisionRepository::class.java)
    private val references = mock(WikiReferenceRepository::class.java)
    private val events = mock(ApplicationEventPublisher::class.java)
    private val actor = Actor(an = 9)
    private val account = Account(an = 9, name = "검증")
    private val body = WikiBody(blocks = Json.readTree("""[{"w":"p","ch":["새 검색 본문"]}]"""))
    private val service = WikiContentService(access, mock(AnimeRepository::class.java), documents, templates, revisions, references,
        mock(WikiImageService::class.java), mock(ContentSearchRepository::class.java), events)

    @Test
    fun `new and revised anime wiki emit their anime ID`() {
        `when`(access.requireContributor(actor)).thenReturn(account)
        `when`(documents.lockAnime(123)).thenReturn(Anime(animeNo = 123, subject = "작품"))
        service.editAnime(123, WikiEditRequest(0, body), actor)
        val event = ArgumentCaptor.forClass(SearchChangedEvent::class.java)
        verify(events).publishEvent(event.capture())
        assertEquals(SearchKind.ANIME, event.value.kind)
        assertEquals(listOf("123"), event.value.ids)
    }

    @Test
    fun `template edits removals and recoveries all emit a template event`() {
        `when`(access.requireContributor(actor)).thenReturn(account)
        `when`(access.requireOperator(actor)).thenReturn(account)
        val template = WikiTemplate(templateNo = 7, title = "공통").apply { revision = 1; this.body = Json.write(this@SearchMutationEventsTest.body) }
        `when`(templates.lockTemplate(7)).thenReturn(template)
        service.editTemplate(7, WikiEditRequest(1, body, title = "변경된 공통"), actor)
        service.deleteTemplate(7, 2, actor)
        service.recoverTemplate(7, 3, actor)
        val event = ArgumentCaptor.forClass(SearchChangedEvent::class.java)
        verify(events, times(3)).publishEvent(event.capture())
        assertTrue(event.allValues.all { it.kind == SearchKind.TEMPLATE && it.ids == listOf("7") })
    }

    @Test
    fun `restoring a historical wiki uses the same anime indexing path`() {
        `when`(access.requireContributor(actor)).thenReturn(account)
        `when`(documents.lockAnime(123)).thenReturn(Anime(animeNo = 123, subject = "작품"))
        `when`(documents.lockWiki(123)).thenReturn(AnimeWiki(123).apply { revision = 3; this.body = Json.write(WikiBody.empty()) })
        `when`(revisions.findByKindAndTargetNoAndRevision("anime", 123, 1)).thenReturn(WikiRevision(kind = "anime", targetNo = 123, revision = 1, body = Json.write(body)))
        service.restore("anime", 123, 1, WikiRestoreRequest(3), actor)
        val event = ArgumentCaptor.forClass(SearchChangedEvent::class.java)
        verify(events).publishEvent(event.capture())
        assertEquals(SearchKind.ANIME, event.value.kind)
        assertEquals(listOf("123"), event.value.ids)
    }
}
