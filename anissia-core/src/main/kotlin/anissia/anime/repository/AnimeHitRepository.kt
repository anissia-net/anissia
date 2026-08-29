package anissia.anime.repository

import anissia.anime.domain.AnimeHit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface AnimeHitRepository : JpaRepository<AnimeHit, Long> {

    @Modifying
    @Query("DELETE FROM AnimeHit WHERE hour < :hour")
    fun deleteByHourLessThan(hour: Long): Int
}
