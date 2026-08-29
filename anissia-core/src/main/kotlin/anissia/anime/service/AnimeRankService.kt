package anissia.anime.service

import anissia.anime.api.dto.AnimeRankItem
import anissia.anime.domain.AnimeHit
import anissia.anime.repository.AnimeHitHourRepository
import anissia.anime.repository.AnimeHitRepository
import anissia.anime.repository.jooq.AnimeHitQueryRepository
import anissia.security.Actor
import anissia.store.domain.Store
import anissia.store.repository.StoreRepository
import anissia.support.DateFormats
import anissia.support.Json
import anissia.support.getOrLoad
import anissia.support.ttlCache
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.minutes

@Service
class AnimeRankService(
    private val storeRepository: StoreRepository,
    private val animeHitRepository: AnimeHitRepository,
    private val animeHitHourRepository: AnimeHitHourRepository,
    private val animeHitQueryRepository: AnimeHitQueryRepository,
) {
    private val cache = ttlCache<String, List<Map<*, *>>>(5.minutes)

    @Transactional(readOnly = true)
    fun get(type: String): List<Map<*, *>> = cache.getOrLoad(type) {
        when (it) {
            "week", "quarter", "year" ->
                Json.read(storeRepository.findByIdOrNull("rank.$it")?.data ?: "[]")

            else -> listOf()
        }
    }

    @Async
    @Transactional
    fun hit(animeNo: Long, actor: Actor) {
        animeHitRepository.save(
            AnimeHit(
                animeNo = animeNo,
                ip = actor.ip,
                hour = OffsetDateTime.now().format(DateFormats.RANK_HOUR).toLong(),
            ),
        )
    }

    @Transactional
    fun renew() {
        animeHitHourRepository.deleteByHourLessThan(
            LocalDateTime.now().minusDays(1000).format(DateFormats.RANK_HOUR).toLong(),
        )
        mergeAnimeHit()
        extractAllRank()
    }

    private fun mergeAnimeHit() {
        val hour = LocalDateTime.now().format(DateFormats.RANK_HOUR).toLong()
        animeHitQueryRepository.upsertHourlyHits(animeHitQueryRepository.aggregateHourlyHits(hour))
        animeHitRepository.deleteByHourLessThan(hour)

        animeHitQueryRepository.mergeByDay(60)
        animeHitQueryRepository.deleteMergedByDay(60)

        animeHitQueryRepository.mergeByMonth(400)
        animeHitQueryRepository.deleteMergedByMonth(400)
    }

    private fun extractAllRank() {
        val now = LocalDateTime.now()

        val day392 = extractRank(now.minusDays(392))
        val day364 = extractRank(now.minusDays(364)).apply { calculateRankDiff(this, day392) }

        val day112 = extractRank(now.minusDays(112))
        val day84 = extractRank(now.minusDays(84)).apply { calculateRankDiff(this, day112) }

        val day14 = extractRank(now.minusDays(14))
        val day7 = extractRank(now.minusDays(7)).apply { calculateRankDiff(this, day14) }

        storeRepository.save(Store("rank.week", "", toStoredJson(day7)))
        storeRepository.save(Store("rank.quarter", "", toStoredJson(day84)))
        storeRepository.save(Store("rank.year", "", toStoredJson(day364)))
        cache.invalidateAll()
    }

    private fun toStoredJson(list: List<AnimeRankItem>): String =
        Json.write(if (list.size > 30) list.subList(0, 30) else list)

    private fun extractRank(from: LocalDateTime): List<AnimeRankItem> =
        animeHitQueryRepository
            .extractRank(from.format(DateFormats.RANK_HOUR).toLong())
            .filter { it.exist }
            .apply { calculateRank(this) }

    private fun calculateRank(rankList: List<AnimeRankItem>) {
        var rank = 0
        var hit = -1L
        rankList.forEachIndexed { index, node ->
            if (node.hit != hit) {
                hit = node.hit
                rank = index + 1
            }
            node.rank = rank
        }
    }

    private fun calculateRankDiff(rankList: List<AnimeRankItem>, prevRankList: List<AnimeRankItem>) {
        rankList.forEach { now ->
            prevRankList
                .find { prev -> prev.animeNo == now.animeNo }
                ?.also { prev -> now.diff = -(now.rank - prev.rank) }
        }
    }
}
