package anissia.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

@DisplayName("Page 확장 함수")
class PagesTest {

    @Test
    fun `replaceContent 는 동일한 pageable과 totalElements 를 유지하며 컨텐츠만 교체한다`() {
        val original = PageImpl(listOf(1L, 2L, 3L), PageRequest.of(0, 30), 100L)
        val replaced = original.replaceContent(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c"), replaced.content)
        assertEquals(100L, replaced.totalElements)
        assertEquals(original.pageable, replaced.pageable)
    }

    @Test
    fun `filterContent 는 주어진 조건으로 필터링한 컨텐츠를 반환한다`() {
        val origin = PageImpl(listOf(1, 2, 3, 4), PageRequest.of(0, 30), 4L)
        val filtered = origin.filterContent { it % 2 == 0 }
        assertEquals(listOf(2, 4), filtered.content)
        assertEquals(2L, filtered.totalElements)
    }
}
