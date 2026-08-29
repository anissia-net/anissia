package anissia.counter

import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.board.repository.jooq.BoardQueryRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@DisplayName("파생 집계 컬럼 재조정 배치")
class DerivedCounterReconcilerTest {

    private val boardQueryRepository = mock(BoardQueryRepository::class.java)
    private val animeQueryRepository = mock(AnimeQueryRepository::class.java)

    private val reconciler = DerivedCounterReconciler(boardQueryRepository, animeQueryRepository)

    @Test
    fun `불일치가 없으면 전체 갱신을 하지 않는다`() {
        `when`(boardQueryRepository.countPostCountMismatch()).thenReturn(0)
        `when`(animeQueryRepository.countCaptionCountMismatch()).thenReturn(0)

        reconciler.reconcileAll()

        verify(boardQueryRepository, never()).updatePostCountAll()
        verify(animeQueryRepository, never()).updateCaptionCountAll()
    }

    @Test
    fun `불일치가 있는 대상만 전체 갱신한다`() {
        `when`(boardQueryRepository.countPostCountMismatch()).thenReturn(2)
        `when`(animeQueryRepository.countCaptionCountMismatch()).thenReturn(0)

        reconciler.reconcileAll()

        verify(boardQueryRepository).updatePostCountAll()
        verify(animeQueryRepository, never()).updateCaptionCountAll()
    }

    @Test
    fun `대상마다 따로 판단한다`() {
        `when`(boardQueryRepository.countPostCountMismatch()).thenReturn(0)
        `when`(animeQueryRepository.countCaptionCountMismatch()).thenReturn(3)

        reconciler.reconcileAll()

        verify(boardQueryRepository, never()).updatePostCountAll()
        verify(animeQueryRepository).updateCaptionCountAll()
    }
}
