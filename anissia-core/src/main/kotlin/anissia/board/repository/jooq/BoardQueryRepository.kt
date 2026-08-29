package anissia.board.repository.jooq

import anissia.support.jooqColumn
import anissia.support.jooqTable
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Query
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

object BoardTopicTable {
    const val NAME = "board_topic"

    val TABLE = jooqTable(NAME)
    val TOPIC_NO = jooqColumn(NAME, "topic_no", SQLDataType.BIGINT)
    val POST_COUNT = jooqColumn(NAME, "post_count", SQLDataType.INTEGER)
}

object BoardPostTable {
    const val NAME = "board_post"

    val TABLE = jooqTable(NAME)
    val TOPIC_NO = jooqColumn(NAME, "topic_no", SQLDataType.BIGINT)
}

@Repository
class BoardQueryRepository(
    private val dsl: DSLContext,
) {
    /**
     * [topicNos] 의 `post_count` 를 실제 글 수로 다시 집계한다.
     *
     * jOOQ 네이티브 SQL 이라 영속성 컨텍스트를 보지 않는다. 같은 트랜잭션에서 JPA 로 글을
     * 저장하거나 삭제했다면 flush 이후에 호출해야 하므로, 직접 부르는 대신
     * `DerivedCounters.markTopic` 으로 등록해 커밋 직전에 실행되도록 한다.
     */
    @Transactional
    fun updatePostCount(topicNos: Collection<Long>): Int =
        if (topicNos.isEmpty()) 0 else updatePostCountOf(dsl, topicNos).execute()

    /** `post_count` 가 실제 글 수와 어긋난 글의 수. 0 이 아니면 어딘가 갱신이 누락된 것이다. */
    @Transactional(readOnly = true)
    fun countPostCountMismatch(): Int = postCountMismatchOf(dsl).fetchOne(0, Int::class.java) ?: 0

    /** 모든 글의 `post_count` 를 실제 글 수로 다시 집계한다. 재조정 배치 전용이다. */
    @Transactional
    fun updatePostCountAll(): Int = updatePostCountOfAll(dsl).execute()

    internal companion object {
        fun updatePostCountOf(dsl: DSLContext, topicNos: Collection<Long>): Query =
            dsl.update(BoardTopicTable.TABLE)
                .set(BoardTopicTable.POST_COUNT, postCountOfCurrentRow())
                .where(BoardTopicTable.TOPIC_NO.`in`(topicNos))

        fun updatePostCountOfAll(dsl: DSLContext): Query =
            dsl.update(BoardTopicTable.TABLE)
                .set(BoardTopicTable.POST_COUNT, postCountOfCurrentRow())

        fun postCountMismatchOf(dsl: DSLContext): Select<Record1<Int>> =
            dsl.selectCount()
                .from(BoardTopicTable.TABLE)
                .where(BoardTopicTable.POST_COUNT.ne(postCountOfCurrentRow()))

        /** 루트 글(본문)은 글 수에서 제외한다. */
        private fun postCountOfCurrentRow(): Field<Int> =
            DSL.field(
                DSL.select(DSL.count())
                    .from(BoardPostTable.TABLE)
                    .where(BoardPostTable.TOPIC_NO.eq(BoardTopicTable.TOPIC_NO)),
            ).minus(1)
    }
}
