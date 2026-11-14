package anissia.anime

import anissia.account.repository.AccountRepository
import anissia.activepanel.repository.ActivePanelRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.agenda.domain.Agenda
import anissia.agenda.repository.AgendaRepository
import anissia.anime.api.dto.AnimeItem
import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeCaption
import anissia.anime.repository.*
import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.anime.service.*
import anissia.counter.DerivedCounters
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.Json
import anissia.translator.service.TranslatorApplyService
import anissia.wiki.WikiContentService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import java.time.OffsetDateTime

class AnimeDeletionRetentionTest {
    private val anime = mock(AnimeRepository::class.java)
    private val captions = mock(AnimeCaptionRepository::class.java)
    private val agendas = mock(AgendaRepository::class.java)
    private val log = mock(ActivePanelLogService::class.java)
    private val events = mock(ApplicationEventPublisher::class.java)
    private val grants = mock(TranslatorApplyService::class.java)
    private val counters = mock(DerivedCounters::class.java)
    private val hits = mock(AnimeHitRepository::class.java)
    private val hourlyHits = mock(AnimeHitHourRepository::class.java)
    private val wiki = mock(WikiContentService::class.java)
    private val actor = Actor(an = 7, name = "운영자", roles = listOf("ROOT"))
    private val source = Anime(animeNo = 42, subject = "보존 작품", note = "기존 메모", thumbnail = "https://file.invalid/image.avif",
        captions = listOf(AnimeCaption(an = 9, website = "https://caption.invalid")))
    private val service = AnimeService(anime, captions, mock(AnimeGenreRepository::class.java), mock(AnimeQueryRepository::class.java),
        counters, mock(AnimeSearchRepository::class.java), mock(AnimeRankService::class.java), log,
        mock(ActivePanelRepository::class.java), agendas, mock(AccountRepository::class.java), grants, events)
    private val deletion = AnimeDeletionService(anime, captions, hits, hourlyHits, agendas, wiki, events)

    @Test
    fun `deleting hides the existing row and preserves captions and wiki until expiration`() {
        `when`(grants.isGrantedBefore90Days(actor.an)).thenReturn(true)
        `when`(anime.lockByAnimeNo(42)).thenReturn(source)

        assertEquals(ApiResponse.OK, service.delete(42, actor).code)

        assertNotNull(source.deletedAt)
        verify(anime).save(source)
        verify(anime, never()).delete(source)
        verifyNoInteractions(captions, wiki, counters)
        val saved = ArgumentCaptor.forClass(Agenda::class.java)
        verify(agendas).save(saved.capture())
        assertEquals("retained:42", saved.value.data2)
        val snapshot = Json.read<AnimeItem>(saved.value.data1!!)
        assertEquals(42L, snapshot.animeNo)
        assertEquals(1, snapshot.captions.size)
        assertEquals(source.deletedAt, OffsetDateTime.parse(snapshot.deletedAt))
        assertEquals(source.deletedAt!!.plusDays(30), OffsetDateTime.parse(snapshot.purgeAfter))
    }

    @Test
    fun `repeated deletion does not move the expiration date or create another request`() {
        val deletedAt = OffsetDateTime.now().minusDays(7)
        source.deletedAt = deletedAt
        `when`(grants.isGrantedBefore90Days(actor.an)).thenReturn(true)
        `when`(anime.lockByAnimeNo(42)).thenReturn(source)

        assertEquals(ApiResponse.FAIL, service.delete(42, actor).code)
        assertEquals(deletedAt, source.deletedAt)
        verifyNoInteractions(agendas, captions, wiki, events)
    }

    @Test
    fun `recovery restores the same ID without rebuilding dependent records`() {
        source.deletedAt = OffsetDateTime.now().minusDays(29)
        val agenda = Agenda(agendaNo = 3, code = "ANIME-DEL", status = "wait", data2 = "retained:42")
        `when`(agendas.findDeletionReference(3)).thenReturn(agenda.data2)
        `when`(agendas.lockByAgendaNo(3)).thenReturn(agenda)
        `when`(anime.lockByAnimeNo(42)).thenReturn(source)

        val result = service.recover(3, actor)

        assertEquals(ApiResponse.OK, result.code)
        assertEquals(42L, result.data)
        assertNull(source.deletedAt)
        assertEquals("recover", agenda.status)
        assertEquals("기존 메모", source.note)
        assertEquals("https://file.invalid/image.avif", source.thumbnail)
        verify(anime).save(source)
        verifyNoInteractions(captions, wiki, counters)
        inOrder(anime, agendas).run {
            verify(agendas).findDeletionReference(3)
            verify(anime).lockByAnimeNo(42)
            verify(agendas).lockByAgendaNo(3)
        }
    }

    @Test
    fun `purged retained requests never fall back to legacy reconstruction`() {
        val agenda = Agenda(agendaNo = 3, code = "ANIME-DEL", status = "wait", data2 = "retained:42", data1 = Json.write(AnimeItem(source)))
        `when`(agendas.findDeletionReference(3)).thenReturn(agenda.data2)
        `when`(agendas.lockByAgendaNo(3)).thenReturn(agenda)

        assertEquals(ApiResponse.FAIL, service.recover(3, actor).code)
        verifyNoInteractions(captions, counters, wiki, events)
        verify(anime, never()).save(any(Anime::class.java))
    }

    @Test
    fun `old fully deleted snapshots retain their recovery path`() {
        val snapshot = Json.write(AnimeItem(source))
        val agenda = Agenda(agendaNo = 3, code = "ANIME-DEL", status = "wait", data1 = snapshot)
        `when`(agendas.lockByAgendaNo(3)).thenReturn(agenda)
        `when`(anime.save(any(Anime::class.java))).thenAnswer { invocation ->
            (invocation.arguments[0] as Anime).apply { animeNo = 84 }
        }

        val result = service.recover(3, actor)

        assertEquals(ApiResponse.OK, result.code)
        assertEquals(84L, result.data)
        assertEquals("recover", agenda.status)
        val saved = ArgumentCaptor.forClass(Anime::class.java)
        verify(anime).save(saved.capture())
        assertEquals(source.note, saved.value.note)
        assertEquals(source.thumbnail, saved.value.thumbnail)
    }

    @Test
    fun `purge rechecks the locked row and does not delete a recovered work`() {
        `when`(anime.lockByAnimeNo(42)).thenReturn(source)
        assertFalse(deletion.purge(42, OffsetDateTime.now().minusDays(30)))
        verifyNoInteractions(wiki, captions, agendas, hits, hourlyHits, events)
    }

    @Test
    fun `purge does not remove a more recent deletion from a stale batch candidate`() {
        val cutoff = OffsetDateTime.now().minusDays(30)
        source.deletedAt = cutoff.plusNanos(1000)
        `when`(anime.lockByAnimeNo(42)).thenReturn(source)
        assertFalse(deletion.purge(42, cutoff))
        verifyNoInteractions(wiki, captions, agendas, hits, hourlyHits, events)
    }

    @Test
    fun `purge deletes an expired work and its dependent records at the boundary`() {
        val cutoff = OffsetDateTime.now().minusDays(30)
        source.deletedAt = cutoff
        `when`(anime.lockByAnimeNo(42)).thenReturn(source)

        assertTrue(deletion.purge(42, cutoff))
        inOrder(anime, wiki, captions, hits, hourlyHits, agendas).run {
            verify(anime).lockByAnimeNo(42)
            verify(wiki).deleteForAnime(42)
            verify(captions).deleteByAnimeNo(42)
            verify(hits).deleteByAnimeNo(42)
            verify(hourlyHits).deleteByAnimeNo(42)
            verify(anime).delete(source)
            verify(anime).flush()
            verify(agendas).finishRetainedDeletion("retained:42")
        }
    }

    @Test
    fun `deleted works are not returned by the public detail API`() {
        source.deletedAt = OffsetDateTime.now()
        `when`(anime.findWithCaptionsByAnimeNo(42)).thenReturn(source)
        assertEquals(0L, service.get(42, Actor()).animeNo)
    }
}
