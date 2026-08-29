package anissia.anime.repository

import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface AnimeRepository : JpaRepository<Anime, Long> {

    fun findAllByStatusNotAndWeek(status: AnimeStatus, week: String): List<Anime>

    fun findAllByOrderByAnimeNoDesc(pageable: Pageable): Page<Anime>

    fun findAllByAnimeNoInOrderByAnimeNoDesc(animeNo: Collection<Long>): List<Anime>

    fun findAllByAnimeNoIn(animeNo: Collection<Long>): List<Anime>

    fun existsBySubject(subject: String): Boolean

    fun existsBySubjectAndAnimeNoNot(subject: String, animeNo: Long): Boolean

    @EntityGraph(attributePaths = ["captions", "captions.account"])
    fun findWithCaptionsByAnimeNo(animeNo: Long): Anime?
}
