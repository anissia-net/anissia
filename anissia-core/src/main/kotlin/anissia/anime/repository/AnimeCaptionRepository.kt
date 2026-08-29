package anissia.anime.repository

import anissia.anime.domain.AnimeCaption
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

interface AnimeCaptionRepository : JpaRepository<AnimeCaption, AnimeCaption.Key> {

    @EntityGraph(attributePaths = ["account"])
    @Query("SELECT a FROM AnimeCaption a WHERE a.anime.animeNo = :animeNo ORDER BY a.updDt DESC")
    fun findAllWithAccountByAnimeNo(animeNo: Long): List<AnimeCaption>

    @EntityGraph(attributePaths = ["anime"])
    @Query(
        "SELECT a FROM AnimeCaption a JOIN a.anime b WHERE a.an = :an AND b.status <> anissia.anime.domain.AnimeStatus.END ORDER BY a.updDt DESC",
    )
    fun findAllActiveWithAnimeByAn(an: Long, pageable: Pageable): Page<AnimeCaption>

    @EntityGraph(attributePaths = ["anime"])
    @Query(
        "SELECT a FROM AnimeCaption a JOIN a.anime b WHERE a.an = :an AND b.status = anissia.anime.domain.AnimeStatus.END ORDER BY a.updDt DESC",
    )
    fun findAllEndedWithAnimeByAn(an: Long, pageable: Pageable): Page<AnimeCaption>

    @Modifying
    @Query("DELETE FROM AnimeCaption a WHERE a.anime.animeNo = :animeNo")
    fun deleteByAnimeNo(animeNo: Long): Int

    fun findAllByAn(an: Long): List<AnimeCaption>

    @Query("SELECT a.anime.animeNo FROM AnimeCaption a WHERE a.an = :an")
    fun findAnimeNosByAn(an: Long): List<Long>

    @Modifying
    @Query("DELETE FROM AnimeCaption a WHERE a.an = :an")
    fun deleteAllByAn(an: Long): Int

    @EntityGraph(attributePaths = ["account", "anime"])
    fun findAllByUpdDtAfterAndWebsiteNotOrderByUpdDtDesc(
        pageable: Pageable,
        updDt: OffsetDateTime = OffsetDateTime.now().minusDays(90),
        website: String = "",
    ): Page<AnimeCaption>
}
