package anissia.counter

import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.board.repository.jooq.BoardQueryRepository
import jakarta.persistence.EntityManagerFactory
import org.springframework.orm.jpa.EntityManagerFactoryUtils
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * `board_topic.post_count`, `anime.caption_count` 처럼 다른 테이블에서 파생되는 집계 컬럼을 갱신한다.
 *
 * 집계 UPDATE 는 jOOQ 네이티브 SQL 이라 영속성 컨텍스트를 거치지 않는다. 그래서 JPA 로 저장하거나
 * 삭제한 내용이 flush 되기 전에 실행하면 변경 이전의 행을 세게 되고, 호출부마다 flush 시점을
 * 기억해야 하는 숨은 계약이 생긴다. 그 계약을 없애기 위해 호출부는 갱신 대상만 등록해두고,
 * 실제 집계는 커밋 직전에 flush -> 집계 순서로 모아서 한 번에 실행한다.
 *
 * - 호출부는 JPA 쓰기와의 순서를 신경 쓰지 않아도 된다.
 * - 한 트랜잭션에서 여러 번 등록된 대상은 한 번만 갱신된다.
 * - readOnly 트랜잭션에서는 아무 것도 하지 않는다.
 *
 * 집계 UPDATE 가 실패하면 등록한 쓰기까지 함께 롤백된다.
 */
@Component
class DerivedCounters(
    private val entityManagerFactory: EntityManagerFactory,
    private val boardQueryRepository: BoardQueryRepository,
    private val animeQueryRepository: AnimeQueryRepository,
) {
    fun markTopic(topicNo: Long) {
        pending().topicNos += topicNo
    }

    fun markTopics(topicNos: Collection<Long>) {
        pending().topicNos += topicNos
    }

    fun markAnime(animeNo: Long) {
        pending().animeNos += animeNo
    }

    fun markAnimes(animeNos: Collection<Long>) {
        pending().animeNos += animeNos
    }

    /**
     * 현재 트랜잭션에 등록된 [Pending] 을 반환하고, 없으면 새로 등록한다.
     *
     * 트랜잭션 동기화는 REQUIRES_NEW 로 트랜잭션이 중단되면 스프링이 함께 대기시켰다가 재개하므로,
     * 중첩된 트랜잭션은 바깥 트랜잭션의 대기열을 보지 않고 자기 것을 새로 만든다.
     */
    private fun pending(): Pending {
        check(TransactionSynchronizationManager.isSynchronizationActive()) {
            "DerivedCounters 는 트랜잭션 안에서만 호출할 수 있습니다."
        }
        return TransactionSynchronizationManager.getSynchronizations()
            .filterIsInstance<Pending>()
            .firstOrNull()
            ?: Pending().also(TransactionSynchronizationManager::registerSynchronization)
    }

    private inner class Pending : TransactionSynchronization {
        val topicNos = mutableSetOf<Long>()
        val animeNos = mutableSetOf<Long>()

        override fun beforeCommit(readOnly: Boolean) {
            if (readOnly) {
                return
            }
            EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory)?.flush()
            boardQueryRepository.updatePostCount(topicNos)
            animeQueryRepository.updateCaptionCount(animeNos)
        }
    }
}
