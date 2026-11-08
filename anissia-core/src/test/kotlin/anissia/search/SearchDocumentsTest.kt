package anissia.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.jdbc.core.JdbcTemplate

class SearchDocumentsTest {
    @Test
    fun `single and batch anime documents omit included template text`() {
        val jdbc = mock(JdbcTemplate::class.java)
        val anime = mapOf<String, Any>("anime_no" to 1L, "subject" to "우주 여행")
        val wiki = """{"blocks":[{"w":"p","ch":["작품 설명",{"w":"exTemplateInclude","a":{"exTemplateNo":"7"}}]}]}"""
        val template = mapOf<String, Any>("template_no" to 7L, "title" to "시리즈 목록", "revision" to 1L,
            "body" to """{"blocks":[{"w":"p","ch":["카우보이비밥"]}]}""")
        `when`(jdbc.queryForList("SELECT * FROM anime WHERE anime_no IN (?)", "1")).thenReturn(listOf(anime))
        `when`(jdbc.queryForList("SELECT anime_no, body FROM anime_wiki WHERE anime_no IN (?)", "1"))
            .thenReturn(listOf(mapOf("anime_no" to 1L, "body" to wiki)))
        `when`(jdbc.queryForList("SELECT template_no, title, body, revision FROM wiki_template WHERE template_no IN (?) AND deleted = false", 7L))
            .thenReturn(listOf(template))
        `when`(jdbc.queryForList("SELECT * FROM wiki_template WHERE template_no IN (?)", "7")).thenReturn(listOf(template))

        val documents = SearchDocuments(jdbc)
        assertEquals("작품 설명", documents.load(SearchKind.ANIME, "1")?.get("body"))
        assertEquals("작품 설명", documents.loadBatch(SearchKind.ANIME, listOf("1")).getValue("1")["body"])
        assertEquals("카우보이비밥", documents.load(SearchKind.TEMPLATE, "7")?.get("body"))
    }

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
