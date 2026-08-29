package anissia.anime.repository.jooq

import anissia.anime.api.dto.AnimeRankItem
import org.jooq.DSLContext
import org.jooq.Query
import org.jooq.Record3
import org.jooq.Select
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

class HourlyHit(
    val hour: Long,
    val animeNo: Long,
    val hit: Long,
)

@Repository
class AnimeHitQueryRepository(
    private val dsl: DSLContext,
) {
    @Transactional(readOnly = true)
    fun aggregateHourlyHits(beforeHour: Long): List<HourlyHit> =
        hourlyHits(dsl, beforeHour).fetch { record ->
            HourlyHit(
                hour = record.value1(),
                animeNo = record.value2(),
                hit = record.value3().toLong(),
            )
        }

    @Transactional
    fun upsertHourlyHits(hits: List<HourlyHit>): Int {
        if (hits.isEmpty()) {
            return 0
        }
        return dsl.batch(hits.map { upsertHourlyHit(dsl, it) }).execute().sum()
    }

    @Transactional(readOnly = true)
    fun extractRank(startHour: Long, limit: Int = 100): List<AnimeRankItem> =
        rank(dsl, startHour, limit).fetch { record ->
            AnimeRankItem(
                animeNo = record.value1(),
                subject = record.value2() ?: "",
                hit = record.value3()?.toLong() ?: 0L,
            )
        }

    @Transactional
    fun mergeByDay(beforeDays: Int): Int = dsl.execute(MERGE_BY_DAY, beforeDays)

    @Transactional
    fun deleteMergedByDay(beforeDays: Int): Int = dsl.execute(DELETE_MERGED_BY_DAY, beforeDays)

    @Transactional
    fun mergeByMonth(beforeDays: Int): Int = dsl.execute(MERGE_BY_MONTH, beforeDays)

    @Transactional
    fun deleteMergedByMonth(beforeDays: Int): Int = dsl.execute(DELETE_MERGED_BY_MONTH, beforeDays)

    internal companion object {
        fun hourlyHits(dsl: DSLContext, beforeHour: Long): Select<Record3<Long, Long, Int>> =
            dsl.select(
                AnimeHitTable.HOUR,
                AnimeHitTable.ANIME_NO,
                DSL.countDistinct(AnimeHitTable.IP),
            )
                .from(AnimeHitTable.TABLE)
                .where(AnimeHitTable.HOUR.lt(beforeHour))
                .groupBy(AnimeHitTable.HOUR, AnimeHitTable.ANIME_NO)

        fun upsertHourlyHit(dsl: DSLContext, hit: HourlyHit): Query =
            dsl.insertInto(AnimeHitHourTable.TABLE)
                .columns(AnimeHitHourTable.HOUR, AnimeHitHourTable.ANIME_NO, AnimeHitHourTable.HIT)
                .values(hit.hour, hit.animeNo, hit.hit)
                .onDuplicateKeyUpdate()
                .set(AnimeHitHourTable.HIT, AnimeHitHourTable.HIT.add(hit.hit))

        fun rank(dsl: DSLContext, startHour: Long, limit: Int): Select<Record3<Long, String, BigDecimal>> {
            val totalHit = DSL.sum(AnimeHitHourTable.HIT)
            return dsl.select(AnimeHitHourTable.ANIME_NO, AnimeTable.SUBJECT, totalHit)
                .from(AnimeHitHourTable.TABLE)
                .leftJoin(AnimeTable.TABLE)
                .on(AnimeTable.ANIME_NO.eq(AnimeHitHourTable.ANIME_NO))
                .where(AnimeHitHourTable.HOUR.ge(startHour))
                .groupBy(AnimeHitHourTable.ANIME_NO, AnimeTable.SUBJECT)
                .orderBy(totalHit.desc())
                .limit(limit)
        }

        const val MERGE_BY_DAY = """
            insert into anime_hit_hour (hour, anime_no, hit)
            select ym, anime_no, sum(hit) hit from (select concat(left(hour, length(hour) - 2), '00') ym, anime_no, hit from anime_hit_hour
            where hour < cast(date_format(date_sub(curdate(), interval ? day), '%Y%m%d00') as unsigned) and hour % 100 != 0) a
            group by ym, anime_no on duplicate key update hit = hit + values(hit)
        """

        const val DELETE_MERGED_BY_DAY = """
            delete from anime_hit_hour
            where hour < cast(date_format(date_sub(curdate(), interval ? day), '%Y%m%d00') as unsigned) and hour % 100 != 0
        """

        const val MERGE_BY_MONTH = """
            insert into anime_hit_hour (hour, anime_no, hit)
            select ym, anime_no, sum(hit) hit from (select concat(left(hour, length(hour) - 4), '0000') ym, anime_no, hit from anime_hit_hour
            where hour < cast(date_format(date_sub(curdate(), interval ? day), '%Y%m%d00') as unsigned) and hour % 10000 != 0) a
            group by ym, anime_no on duplicate key update hit = hit + values(hit)
        """

        const val DELETE_MERGED_BY_MONTH = """
            delete from anime_hit_hour
            where hour < cast(date_format(date_sub(curdate(), interval ? day), '%Y%m%d00') as unsigned) and hour % 10000 != 0
        """
    }
}
