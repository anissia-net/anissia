package anissia.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SearchDocumentsTest {
    @Test
    fun `partial airing dates sort after known dates in the same period`() {
        assertEquals(20260201L, animeStartDateSort("2026-02-01"))
        assertEquals(20260200L, animeStartDateSort("2026-02-99"))
        assertEquals(20260000L, animeStartDateSort("2026-99-99"))
        assertEquals(19990101L, animeStartDateSort("1999-01-01"))
        assertNull(animeStartDateSort(""))
        assertNull(animeStartDateSort("미정"))
    }
}
