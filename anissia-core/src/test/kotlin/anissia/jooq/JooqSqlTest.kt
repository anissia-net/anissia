package anissia.jooq

import anissia.anime.repository.jooq.AnimeHitQueryRepository
import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.anime.repository.jooq.HourlyHit
import anissia.board.repository.jooq.BoardQueryRepository
import org.jooq.SQLDialect
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("jOOQ 렌더링 SQL 검증 (MariaDB)")
class JooqSqlTest {

    private val dsl = DSL.using(SQLDialect.MARIADB)

    private fun sql(query: org.jooq.Query): String =
        query.getSQL(ParamType.INLINED).replace(Regex("\\s+"), " ").trim()

    @Test
    fun `자동완성 조회는 anime_no 와 subject 를 이어붙이고 10건으로 제한한다`() {
        val rendered = sql(AnimeQueryRepository.autocorrectTop10(dsl, "ㄱㅏ"))
        assertTrue(rendered.startsWith("select concat("), rendered)
        assertTrue(rendered.contains("from `anime`"), rendered)
        assertTrue(rendered.contains("`anime`.`autocorrect` like"), rendered)
        assertTrue(rendered.contains("10 rows only") || rendered.contains("limit 10"), rendered)
    }

    @Test
    fun `자막제작자 이름 조회는 anime_caption 서브쿼리를 사용한다`() {
        val rendered = sql(AnimeQueryRepository.translatorNames(dsl, 7))
        assertEquals(
            "select `account`.`name` from `account` where `account`.`an` in " +
                "(select `anime_caption`.`an` from `anime_caption` where `anime_caption`.`anime_no` = 7)",
            rendered,
        )
    }

    @Test
    fun `caption_count 갱신은 상관 서브쿼리로 개수를 센다`() {
        val rendered = sql(AnimeQueryRepository.updateCaptionCountOf(dsl, listOf(3L)))
        assertEquals(
            "update `anime` set `anime`.`caption_count` = " +
                "(select count(*) from `anime_caption` where `anime_caption`.`anime_no` = `anime`.`anime_no`) " +
                "where `anime`.`anime_no` in (3)",
            rendered,
        )
    }

    @Test
    fun `caption_count 일괄 갱신은 in 절을 사용한다`() {
        val rendered = sql(AnimeQueryRepository.updateCaptionCountOf(dsl, listOf(1L, 2L)))
        assertTrue(rendered.contains("where `anime`.`anime_no` in (1, 2)"), rendered)
    }

    @Test
    fun `caption_count 불일치 조회는 실제 자막 수와 다른 작품을 센다`() {
        val rendered = sql(AnimeQueryRepository.captionCountMismatchOf(dsl))
        assertEquals(
            "select count(*) from `anime` where `anime`.`caption_count` <> " +
                "(select count(*) from `anime_caption` where `anime_caption`.`anime_no` = `anime`.`anime_no`)",
            rendered,
        )
    }

    @Test
    fun `caption_count 전체 갱신은 where 절이 없다`() {
        val rendered = sql(AnimeQueryRepository.updateCaptionCountOfAll(dsl))
        assertTrue(rendered.startsWith("update `anime` set `anime`.`caption_count` = (select count(*)"), rendered)
        assertTrue(!rendered.contains("where `anime`.`anime_no`"), rendered)
    }

    @Test
    fun `시간별 조회수 집계는 ip 를 중복 제거하여 센다`() {
        val rendered = sql(AnimeHitQueryRepository.hourlyHits(dsl, 2026010100))
        assertEquals(
            "select `anime_hit`.`hour`, `anime_hit`.`anime_no`, count(distinct `anime_hit`.`ip`) " +
                "from `anime_hit` where `anime_hit`.`hour` < 2026010100 " +
                "group by `anime_hit`.`hour`, `anime_hit`.`anime_no`",
            rendered,
        )
    }

    @Test
    fun `시간별 조회수 upsert 는 기존 hit 에 더한다`() {
        val rendered = sql(AnimeHitQueryRepository.upsertHourlyHit(dsl, HourlyHit(2026010100, 5, 3)))
        assertEquals(
            "insert into `anime_hit_hour` (`hour`, `anime_no`, `hit`) values (2026010100, 5, 3) " +
                "on duplicate key update `anime_hit_hour`.`hit` = (`anime_hit_hour`.`hit` + 3)",
            rendered,
        )
    }

    @Test
    fun `순위 추출은 anime 과 left join 하고 합계 기준 내림차순 정렬한다`() {
        val rendered = sql(AnimeHitQueryRepository.rank(dsl, 2026010100, 100))
        assertTrue(rendered.contains("sum(`anime_hit_hour`.`hit`)"), rendered)
        assertTrue(rendered.contains("left outer join `anime`"), rendered)
        assertTrue(rendered.contains("group by `anime_hit_hour`.`anime_no`, `anime`.`subject`"), rendered)
        assertTrue(rendered.contains("order by sum(`anime_hit_hour`.`hit`) desc"), rendered)
        assertTrue(rendered.contains("100 rows only") || rendered.contains("limit 100"), rendered)
    }

    @Test
    fun `post_count 갱신은 루트글을 제외하기 위해 1을 뺀다`() {
        val rendered = sql(BoardQueryRepository.updatePostCountOf(dsl, listOf(9L)))
        assertEquals(
            "update `board_topic` set `board_topic`.`post_count` = " +
                "((select count(*) from `board_post` where `board_post`.`topic_no` = `board_topic`.`topic_no`) - 1) " +
                "where `board_topic`.`topic_no` in (9)",
            rendered,
        )
    }

    @Test
    fun `post_count 일괄 갱신은 in 절을 사용한다`() {
        val rendered = sql(BoardQueryRepository.updatePostCountOf(dsl, listOf(4L, 9L)))
        assertTrue(rendered.contains("where `board_topic`.`topic_no` in (4, 9)"), rendered)
    }

    @Test
    fun `post_count 전체 갱신은 where 절이 없다`() {
        val rendered = sql(BoardQueryRepository.updatePostCountOfAll(dsl))
        assertTrue(rendered.startsWith("update `board_topic` set `board_topic`.`post_count` = (("), rendered)
        assertTrue(!rendered.contains("where `board_topic`.`topic_no`"), rendered)
    }

    @Test
    fun `post_count 불일치 조회도 루트글을 제외하고 비교한다`() {
        val rendered = sql(BoardQueryRepository.postCountMismatchOf(dsl))
        assertEquals(
            "select count(*) from `board_topic` where `board_topic`.`post_count` <> " +
                "((select count(*) from `board_post` where `board_post`.`topic_no` = `board_topic`.`topic_no`) - 1)",
            rendered,
        )
    }

    @Test
    fun `조회수 병합 SQL 은 바인드 파라미터 하나만 사용한다`() {
        listOf(
            AnimeHitQueryRepository.MERGE_BY_DAY,
            AnimeHitQueryRepository.DELETE_MERGED_BY_DAY,
            AnimeHitQueryRepository.MERGE_BY_MONTH,
            AnimeHitQueryRepository.DELETE_MERGED_BY_MONTH,
        ).forEach { statement ->
            assertEquals(1, statement.count { it == '?' }, statement)
            assertTrue(statement.contains("anime_hit_hour"), statement)
        }
    }
}
