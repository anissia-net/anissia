package anissia.counter

import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.board.repository.jooq.BoardQueryRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.orm.jpa.EntityManagerHolder
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils

@DisplayName("파생 집계 컬럼 갱신 시점 검증")
class DerivedCountersTest {

    private val em = mock(EntityManager::class.java)
    private val emf = mock(EntityManagerFactory::class.java)
    private val boardQueryRepository = mock(BoardQueryRepository::class.java)
    private val animeQueryRepository = mock(AnimeQueryRepository::class.java)

    private val derivedCounters = DerivedCounters(emf, boardQueryRepository, animeQueryRepository)

    @BeforeEach
    fun beginTransaction() {
        TransactionSynchronizationManager.initSynchronization()
        TransactionSynchronizationManager.bindResource(
            emf,
            EntityManagerHolder(em).apply { isSynchronizedWithTransaction = true },
        )
    }

    @AfterEach
    fun endTransaction() {
        TransactionSynchronizationManager.unbindResourceIfPossible(emf)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `등록 시점에는 집계하지 않고 커밋 직전에 flush 후 집계한다`() {
        derivedCounters.markTopic(9)

        verifyNoInteractions(boardQueryRepository)
        verify(em, never()).flush()

        TransactionSynchronizationUtils.triggerBeforeCommit(false)

        inOrder(em, boardQueryRepository).run {
            verify(em).flush()
            verify(boardQueryRepository).updatePostCount(setOf(9L))
        }
    }

    @Test
    fun `같은 대상을 여러 번 등록해도 한 번만 집계한다`() {
        derivedCounters.markTopic(9)
        derivedCounters.markTopic(9)
        derivedCounters.markTopics(listOf(9, 4))
        derivedCounters.markAnime(3)
        derivedCounters.markAnimes(listOf(3, 7))

        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size)

        TransactionSynchronizationUtils.triggerBeforeCommit(false)

        verify(boardQueryRepository).updatePostCount(setOf(9L, 4L))
        verify(animeQueryRepository).updateCaptionCount(setOf(3L, 7L))
        verify(em).flush()
    }

    @Test
    fun `readOnly 트랜잭션에서는 아무 것도 하지 않는다`() {
        derivedCounters.markTopic(9)

        TransactionSynchronizationUtils.triggerBeforeCommit(true)

        verify(em, never()).flush()
        verifyNoInteractions(boardQueryRepository)
        verifyNoInteractions(animeQueryRepository)
    }

    @Test
    fun `트랜잭션 밖에서 등록하면 실패한다`() {
        TransactionSynchronizationManager.clearSynchronization()

        assertThrows<IllegalStateException> { derivedCounters.markTopic(9) }
    }
}
