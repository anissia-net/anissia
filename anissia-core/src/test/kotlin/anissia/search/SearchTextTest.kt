package anissia.search

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SearchTextTest {
    @Test
    fun `wiki extracts current visible strings footnotes and template text without source URLs or markup`() {
        val body = """{"schemaVersion":1,"blocks":[{"w":"p","ch":["친구",{"w":"a","a":{"href":"https://hidden.invalid"},"ch":["학교"]}]},{"w":"fn","a":{"t":"각주"}},{"w":"exTemplateInclude","a":{"exTemplateNo":7}}]}"""
        val template = """{"schemaVersion":1,"blocks":[{"w":"p","ch":["우주"]}]}"""
        assertEquals("친구 학교 각주 우주", SearchText.wiki(body, mapOf(7L to template)))
        assertEquals("친구 학교 각주", SearchText.wiki(body))
    }

    @Test
    fun `board html excludes scripts styles and markup while decoding entities`() {
        val text = SearchText.html("<html><head><style>.hidden{color:red}</style><script>secret()</script></head><body><p>학교 &amp; 친구</p><a href='https://hidden.invalid'>여행</a></body></html>")
        assertTrue(text.contains("학교 & 친구"))
        assertTrue(text.contains("여행"))
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("hidden"))
    }
}
