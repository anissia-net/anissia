package anissia.anime.repository

import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

interface AnimeRepository : JpaRepository<Anime, Long> {

    @Query("select a.subject from Anime a where a.animeNo = :animeNo and a.deletedAt is null")
    fun findSubjectByAnimeNo(animeNo: Long): String?

    @Query("select a from Anime a where a.status <> :status and a.week = :week and a.deletedAt is null")
    fun findAllByStatusNotAndWeek(status: AnimeStatus, week: String): List<Anime>

    @Query("select a from Anime a where a.deletedAt is null order by a.animeNo desc")
    fun findAllByOrderByAnimeNoDesc(pageable: Pageable): Page<Anime>

    @Query("select a from Anime a where a.animeNo in :animeNo and a.deletedAt is null order by a.animeNo desc")
    fun findAllByAnimeNoInOrderByAnimeNoDesc(animeNo: Collection<Long>): List<Anime>

    @Query("select a from Anime a where a.animeNo in :animeNo and a.deletedAt is null")
    fun findAllByAnimeNoIn(animeNo: Collection<Long>): List<Anime>

    fun existsByAnimeNoAndDeletedAtIsNull(animeNo: Long): Boolean

    @Query("select a.animeNo from Anime a where a.animeNo in :animeNos and a.deletedAt is null")
    fun findVisibleAnimeNos(animeNos: Collection<Long>): List<Long>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Anime a where a.animeNo = :animeNo")
    fun lockByAnimeNo(animeNo: Long): Anime?

    @Query("select a.animeNo from Anime a where a.deletedAt <= :cutoff order by a.deletedAt, a.animeNo")
    fun findExpiredDeletionIds(cutoff: OffsetDateTime, pageable: Pageable): List<Long>

    fun existsBySubject(subject: String): Boolean

    fun existsBySubjectAndAnimeNoNot(subject: String, animeNo: Long): Boolean

    @EntityGraph(attributePaths = ["captions", "captions.account"])
    @Query("select a from Anime a where a.animeNo = :animeNo and a.deletedAt is null")
    fun findWithCaptionsByAnimeNo(animeNo: Long): Anime?
}
