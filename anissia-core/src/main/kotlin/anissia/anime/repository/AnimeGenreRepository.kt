package anissia.anime.repository

import anissia.anime.domain.AnimeGenre
import org.springframework.data.jpa.repository.JpaRepository

interface AnimeGenreRepository : JpaRepository<AnimeGenre, String> {
    fun countByGenreIn(genre: List<String>): Long
}
