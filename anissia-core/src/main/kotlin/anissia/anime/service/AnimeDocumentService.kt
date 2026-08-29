package anissia.anime.service

import anissia.anime.domain.Anime
import anissia.anime.repository.AnimeRepository
import anissia.anime.repository.AnimeSearchRepository
import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.config.FanOut
import anissia.support.logger
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

class AnimeDocumentChangedEvent(
    val animeNos: List<Long>,
) {
    constructor(animeNo: Long) : this(listOf(animeNo))
}

@Service
class AnimeDocumentService(
    private val animeRepository: AnimeRepository,
    private val animeQueryRepository: AnimeQueryRepository,
    private val animeSearchRepository: AnimeSearchRepository,
    private val fanOut: FanOut,
) {
    private val log = logger<AnimeDocumentService>()

    @Transactional(readOnly = true)
    fun sync(animeNo: Long) {
        val anime = animeRepository.findByIdOrNull(animeNo)
        if (anime == null) {
            animeSearchRepository.deleteByAnimeNo(animeNo)
        } else {
            sync(anime)
        }
    }

    @Transactional(readOnly = true)
    fun sync(anime: Anime) {
        animeSearchRepository.update(anime, animeQueryRepository.findTranslatorNames(anime.animeNo))
    }

    fun syncAll(animeNos: Collection<Long>) {
        fanOut.forEach(animeNos) { sync(it) }
    }

    fun reset(drop: Boolean) {
        if (drop) {
            animeSearchRepository.dropAndCreateIndex()
            log.info("Reset anime document index")
        }
        animeQueryRepository.updateCaptionCountAll()
        log.info("Updated caption count")
        fanOut.forEach(animeRepository.findAll()) { sync(it) }
        log.info("Updated anime document")
    }
}

@Service
class AnimeDocumentEventListener(
    private val animeDocumentService: AnimeDocumentService,
) {
    private val log = logger<AnimeDocumentEventListener>()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onAnimeDocumentChanged(event: AnimeDocumentChangedEvent) {
        try {
            if (event.animeNos.size == 1) {
                animeDocumentService.sync(event.animeNos.first())
            } else {
                animeDocumentService.syncAll(event.animeNos)
            }
        } catch (e: Exception) {
            log.error("failed to sync anime documents: ${event.animeNos}", e)
        }
    }
}
