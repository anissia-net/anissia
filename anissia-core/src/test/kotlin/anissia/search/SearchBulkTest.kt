package anissia.search

import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import anissia.support.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SearchBulkTest {
    @Test
    fun `bulk keeps document IDs and Unicode bodies with a final newline`() {
        var sent = ""
        val client = object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse {
                assertEquals("POST", method)
                assertEquals("/new_anime/_bulk", endpoint)
                sent = requireNotNull(body)
                return SearchResponse(200, """{"errors":false,"items":[{"index":{"status":201}},{"index":{"status":200}}]}""")
            }
        }
        ContentSearchRepository(client).writeBatch(SearchKind.ANIME, linkedMapOf("12" to mapOf("body" to "첫 줄\n둘째 줄"), "15" to mapOf("title" to "거인")), "new_anime")
        assertTrue(sent.endsWith("\n"))
        val lines = sent.trimEnd().lines().map(Json::readTree)
        assertEquals("12", lines[0].path("index").path("_id").asString())
        assertEquals("첫 줄\n둘째 줄", lines[1].path("body").asString())
        assertEquals("15", lines[2].path("index").path("_id").asString())
    }

    @Test
    fun `HTTP success cannot hide item failures or missing acknowledgements`() {
        for (responseBody in listOf(
            """{"errors":true,"items":[{"index":{"status":400}}]}""",
            """{"errors":false,"items":[]}""",
            """{"errors":false,"items":[{"index":{"status":429}}]}""",
        )) {
            val client = object : SearchClient {
                override fun request(method: String, endpoint: String, body: String?) = SearchResponse(200, responseBody)
            }
            assertThrows<IllegalStateException> { ContentSearchRepository(client).writeBatch(SearchKind.ANIME, mapOf("1" to mapOf("title" to "작품")), "new_anime") }
        }
    }

    @Test
    fun `bulk payloads are bounded by UTF8 bytes`() {
        val payloads = mutableListOf<String>()
        val client = object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse {
                payloads.add(requireNotNull(body))
                return SearchResponse(200, """{"errors":false,"items":[{"index":{"status":201}}]}""")
            }
        }
        val body = "가".repeat(1_500_000)
        ContentSearchRepository(client).writeBatch(SearchKind.ANIME, mapOf("1" to mapOf("body" to body), "2" to mapOf("body" to body)), "new_anime")
        assertEquals(2, payloads.size)
        assertTrue(payloads.all { it.toByteArray().size <= 8 * 1024 * 1024 })
    }
}
