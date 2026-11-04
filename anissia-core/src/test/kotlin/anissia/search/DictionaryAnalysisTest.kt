package anissia.search

import anissia.support.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DictionaryAnalysisTest {
    @Test
    fun `rules reject invalid segmentation duplicates and syntax escapes`() {
        val invalid = listOf(
            DictionaryRule("1", "user-dictionary", "세종시", "세종 구"),
            DictionaryRule("2", "synonyms", "거인", "거인, titan"),
            DictionaryRule("3", "synonyms", "거인", "타이탄=>titan"),
            DictionaryRule("4", "corrections", "title", "Title"),
            DictionaryRule("5", "stopwords", "애니", "추가값"),
        )
        invalid.forEach { rule -> assertThrows(IllegalArgumentException::class.java) { DictionaryAnalysis.validate(rule) } }
        val stop = DictionaryRule("1", "stopwords", "abc", "")
        assertThrows(IllegalArgumentException::class.java) { DictionaryAnalysis.definition(listOf(stop, stop.copy(id = "2", term = "ABC"))) }
    }

    @Test
    fun `uses index-only mixed and single-path search synonyms`() {
        val mapping = Json.readTree(DictionaryAnalysis.definition(listOf(
            DictionaryRule("1", "synonyms", "세종시", "세종특별자치시"),
            DictionaryRule("2", "user-dictionary", "세종시", "세종 시"),
        )))
        val tokenizers = mapping.path("settings").path("analysis").path("tokenizer")
        assertEquals("mixed", tokenizers.path("korean").path("decompound_mode").asString())
        assertEquals("discard", tokenizers.path("korean_synonyms").path("decompound_mode").asString())
        assertEquals("세종시 세종 시", tokenizers.path("korean_synonyms").path("user_dictionary_rules").first().asString())
        assertEquals("dictionary_search", mapping.path("mappings").path("properties").path("body").path("search_analyzer").asString())
    }

    @Test
    fun `longest whole phrase correction is applied once and anime filters remain distinct`() {
        val terms = searchTerms(SearchKind.ANIME, "ㄱ ㄴ ㄷㅁ #판타지 @철수 /완결")
        val index = DictionaryIndex("index", "search_text", mapOf("ㄱ ㄴ" to "ㄷ", "ㄱ" to "단어", "ㄷ" to "다시"), emptySet())
        assertEquals("ㄷ ㄷㅁ", index.correct(terms.words.joinToString(" ")))
        assertEquals(listOf("판타지"), terms.genres)
        assertEquals(listOf("철수"), terms.translators)
        assertTrue(terms.ended)
    }
}
