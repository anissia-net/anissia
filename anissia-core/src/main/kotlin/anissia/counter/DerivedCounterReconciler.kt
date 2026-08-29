package anissia.counter

import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.board.repository.jooq.BoardQueryRepository
import anissia.support.logger
import org.springframework.stereotype.Component

/**
 * 파생 집계 컬럼이 실제 행 수와 어긋나 있는지 확인하고, 어긋났으면 전체를 다시 집계한다.
 *
 * [DerivedCounters] 가 쓰기 시점의 갱신을 책임지지만, 새 코드가 등록을 빠뜨리거나 DB 를 직접
 * 손대면 값이 어긋날 수 있다. 그런 경우에도 하루 안에 스스로 복구되도록 하는 안전망이다.
 *
 * 불일치가 발견되면 경고 로그를 남긴다. 재조정이 조용히 값만 고치면 정작 원인인 쓰기 경로의
 * 누락은 영영 드러나지 않기 때문에, 고치는 것 자체보다 드러내는 쪽이 더 중요하다.
 *
 * 대상별로 트랜잭션을 따로 잡으므로 한쪽이 실패해도 다른 쪽 재조정은 반영된다.
 */
@Component
class DerivedCounterReconciler(
    private val boardQueryRepository: BoardQueryRepository,
    private val animeQueryRepository: AnimeQueryRepository,
) {
    private val log = logger<DerivedCounterReconciler>()

    fun reconcileAll() {
        reconcile(
            target = "board_topic.post_count",
            countMismatch = boardQueryRepository::countPostCountMismatch,
            updateAll = boardQueryRepository::updatePostCountAll,
        )
        reconcile(
            target = "anime.caption_count",
            countMismatch = animeQueryRepository::countCaptionCountMismatch,
            updateAll = animeQueryRepository::updateCaptionCountAll,
        )
    }

    private fun reconcile(target: String, countMismatch: () -> Int, updateAll: () -> Int) {
        val mismatched = countMismatch()
        if (mismatched == 0) {
            log.info("{} 재조정: 불일치 없음", target)
            return
        }

        updateAll()
        log.warn(
            "{} 재조정: {}건이 실제 행 수와 달라 다시 집계했습니다. 갱신을 누락한 쓰기 경로가 있는지 확인이 필요합니다.",
            target,
            mismatched,
        )
    }
}
