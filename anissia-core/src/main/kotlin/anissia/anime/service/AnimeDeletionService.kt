package anissia.anime.service

import anissia.agenda.repository.AgendaRepository
import anissia.anime.repository.AnimeCaptionRepository
import anissia.anime.repository.AnimeHitHourRepository
import anissia.anime.repository.AnimeHitRepository
import anissia.anime.repository.AnimeRepository
import anissia.support.logger
import anissia.wiki.WikiContentService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class AnimeDeletionService(
    private val anime: AnimeRepository,
    private val captions: AnimeCaptionRepository,
    private val hits: AnimeHitRepository,
    private val hourlyHits: AnimeHitHourRepository,
    private val agendas: AgendaRepository,
    private val wiki: WikiContentService,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun purge(animeNo: Long, cutoff: OffsetDateTime): Boolean {
        val source = anime.lockByAnimeNo(animeNo) ?: return false
        val deletedAt = source.deletedAt ?: return false
        if (deletedAt.isAfter(cutoff)) return false
        wiki.deleteForAnime(animeNo)
        captions.deleteByAnimeNo(animeNo)
        hits.deleteByAnimeNo(animeNo)
        hourlyHits.deleteByAnimeNo(animeNo)
        anime.delete(source)
        anime.flush()
        agendas.finishRetainedDeletion(AnimeService.retentionReference(animeNo))
        events.publishEvent(AnimeDocumentChangedEvent(animeNo))
        return true
    }
}

@Component
class AnimeDeletionBatch(private val anime: AnimeRepository, private val deletion: AnimeDeletionService) {
    private val log = logger<AnimeDeletionBatch>()

    @Scheduled(cron = "0 15 * * * ?")
    fun purgeExpired() {
        val cutoff = OffsetDateTime.now().minusDays(30)
        anime.findExpiredDeletionIds(cutoff, PageRequest.of(0, 100)).forEach { animeNo ->
            try {
                if (deletion.purge(animeNo, cutoff)) log.info("expired anime deleted animeNo={}", animeNo)
            } catch (error: Exception) {
                log.error("expired anime deletion failed animeNo={}", animeNo, error)
            }
        }
    }
}
