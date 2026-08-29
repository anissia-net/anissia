package anissia.anime.service

import anissia.anime.repository.AnimeGenreRepository
import anissia.support.getOrLoad
import anissia.support.ttlCache
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Duration.Companion.minutes

@Service
class AnimeGenreService(
    private val animeGenreRepository: AnimeGenreRepository,
) {
    private val cache = ttlCache<String, List<String>>(60.minutes)

    @Transactional(readOnly = true)
    fun getAll(): List<String> = cache.getOrLoad("genre") {
        animeGenreRepository.findAll().map { it.genre }
    }
}
