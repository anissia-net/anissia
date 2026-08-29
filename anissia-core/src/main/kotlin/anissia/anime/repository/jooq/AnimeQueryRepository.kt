package anissia.anime.repository.jooq

import anissia.account.repository.jooq.AccountTable
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Query
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class AnimeQueryRepository(
    private val dsl: DSLContext,
) {
    @Transactional(readOnly = true)
    fun findAutocorrectTop10(autocorrect: String): List<String> =
        autocorrectTop10(dsl, autocorrect).fetch(0, String::class.java)

    @Transactional(readOnly = true)
    fun findTranslatorNames(animeNo: Long): List<String> =
        translatorNames(dsl, animeNo).fetch(AccountTable.ACCOUNT_NAME)

    /**
     * [animeNos] 의 `caption_count` 를 실제 자막 수로 다시 집계한다.
     *
     * jOOQ 네이티브 SQL 이라 영속성 컨텍스트를 보지 않는다. 같은 트랜잭션에서 JPA 로 자막을
     * 저장하거나 삭제했다면 flush 이후에 호출해야 하므로, 직접 부르는 대신
     * `DerivedCounters.markAnime` 으로 등록해 커밋 직전에 실행되도록 한다.
     */
    @Transactional
    fun updateCaptionCount(animeNos: Collection<Long>): Int =
        if (animeNos.isEmpty()) 0 else updateCaptionCountOf(dsl, animeNos).execute()

    /** `caption_count` 가 실제 자막 수와 어긋난 작품의 수. 0 이 아니면 어딘가 갱신이 누락된 것이다. */
    @Transactional(readOnly = true)
    fun countCaptionCountMismatch(): Int = captionCountMismatchOf(dsl).fetchOne(0, Int::class.java) ?: 0

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun updateCaptionCountAll(): Int = updateCaptionCountOfAll(dsl).execute()

    internal companion object {
        fun autocorrectTop10(dsl: DSLContext, autocorrect: String): Select<Record1<String>> =
            dsl.select(
                DSL.concat(
                    AnimeTable.ANIME_NO.cast(String::class.java),
                    DSL.inline(" "),
                    AnimeTable.SUBJECT,
                ),
            )
                .from(AnimeTable.TABLE)
                .where(AnimeTable.AUTOCORRECT.startsWith(autocorrect))
                .limit(10)

        fun translatorNames(dsl: DSLContext, animeNo: Long): Select<Record1<String>> =
            dsl.select(AccountTable.ACCOUNT_NAME)
                .from(AccountTable.TABLE)
                .where(
                    AccountTable.AN.`in`(
                        DSL.select(AnimeCaptionTable.AN)
                            .from(AnimeCaptionTable.TABLE)
                            .where(AnimeCaptionTable.ANIME_NO.eq(animeNo)),
                    ),
                )

        fun updateCaptionCountOf(dsl: DSLContext, animeNos: Collection<Long>): Query =
            dsl.update(AnimeTable.TABLE)
                .set(AnimeTable.CAPTION_COUNT, captionCountOfCurrentRow())
                .where(AnimeTable.ANIME_NO.`in`(animeNos))

        fun updateCaptionCountOfAll(dsl: DSLContext): Query =
            dsl.update(AnimeTable.TABLE)
                .set(AnimeTable.CAPTION_COUNT, captionCountOfCurrentRow())

        fun captionCountMismatchOf(dsl: DSLContext): Select<Record1<Int>> =
            dsl.selectCount()
                .from(AnimeTable.TABLE)
                .where(AnimeTable.CAPTION_COUNT.ne(captionCountOfCurrentRow()))

        private fun captionCountOfCurrentRow(): Field<Int> =
            DSL.field(
                DSL.select(DSL.count())
                    .from(AnimeCaptionTable.TABLE)
                    .where(AnimeCaptionTable.ANIME_NO.eq(AnimeTable.ANIME_NO)),
            )
    }
}
