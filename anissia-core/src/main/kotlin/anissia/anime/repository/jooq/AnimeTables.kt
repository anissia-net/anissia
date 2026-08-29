package anissia.anime.repository.jooq

import anissia.support.jooqColumn
import anissia.support.jooqTable
import org.jooq.impl.SQLDataType

object AnimeTable {
    const val NAME = "anime"

    val TABLE = jooqTable(NAME)
    val ANIME_NO = jooqColumn(NAME, "anime_no", SQLDataType.BIGINT)
    val SUBJECT = jooqColumn(NAME, "subject", SQLDataType.VARCHAR)
    val AUTOCORRECT = jooqColumn(NAME, "autocorrect", SQLDataType.VARCHAR)
    val CAPTION_COUNT = jooqColumn(NAME, "caption_count", SQLDataType.INTEGER)
}

object AnimeCaptionTable {
    const val NAME = "anime_caption"

    val TABLE = jooqTable(NAME)
    val ANIME_NO = jooqColumn(NAME, "anime_no", SQLDataType.BIGINT)
    val AN = jooqColumn(NAME, "an", SQLDataType.BIGINT)
}

object AnimeHitTable {
    const val NAME = "anime_hit"

    val TABLE = jooqTable(NAME)
    val IP = jooqColumn(NAME, "ip", SQLDataType.VARCHAR)
    val ANIME_NO = jooqColumn(NAME, "anime_no", SQLDataType.BIGINT)
    val HOUR = jooqColumn(NAME, "hour", SQLDataType.BIGINT)
}

object AnimeHitHourTable {
    const val NAME = "anime_hit_hour"

    val TABLE = jooqTable(NAME)
    val HOUR = jooqColumn(NAME, "hour", SQLDataType.BIGINT)
    val ANIME_NO = jooqColumn(NAME, "anime_no", SQLDataType.BIGINT)
    val HIT = jooqColumn(NAME, "hit", SQLDataType.BIGINT)
}
