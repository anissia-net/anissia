package anissia.notification.repository

import anissia.account.domain.Account
import anissia.notification.domain.AnimeNotification
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface AnimeNotificationRepository : JpaRepository<AnimeNotification, AnimeNotification.Key> {

    @EntityGraph(attributePaths = ["anime", "anime.captions", "anime.captions.account"])
    @Query("SELECT DISTINCT n FROM AnimeNotification n JOIN n.anime a WHERE n.an = :an ORDER BY n.createdAt DESC, n.animeNo DESC")
    fun findAllWithAnimeByAn(an: Long): List<AnimeNotification>

    fun countByAn(an: Long): Long

    fun findAllByAnimeNoAndNotifyTrue(animeNo: Long): List<AnimeNotification>

    // 같은 계정의 동시 등록/삭제를 직렬화해 200개 한도와 멱등 등록을 보장한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.an = :an")
    fun lockAccount(an: Long): Account?

    @Modifying
    @Query("DELETE FROM AnimeNotification n WHERE n.an = :an AND n.animeNo = :animeNo")
    fun deleteByAnAndAnimeNo(an: Long, animeNo: Long): Int
}
