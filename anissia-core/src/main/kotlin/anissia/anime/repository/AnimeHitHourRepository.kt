package anissia.anime.repository

import anissia.anime.domain.AnimeHitHour
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface AnimeHitHourRepository : JpaRepository<AnimeHitHour, AnimeHitHour.Key> {

    @Modifying
    @Query("DELETE FROM AnimeHitHour WHERE hour < :hour")
    fun deleteByHourLessThan(hour: Long): Int
}
