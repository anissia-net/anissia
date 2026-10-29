package anissia.search

import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class SearchFailureTest {
    private fun repository(status: Int, payload: String) = ContentSearchRepository(object : SearchClient {
        override fun request(method: String, endpoint: String, body: String?) = SearchResponse(status, payload)
    })

    @Test
    fun `missing index and partial shard failures are errors not zero search results`() {
        for ((status, body) in listOf(404 to "{}", 503 to "{}", 200 to """{"timed_out":true}""", 200 to """{"_shards":{"failed":1}}""")) {
            val error = assertThrows<ResponseStatusException> { repository(status, body).search(SearchKind.ANIME, "검색", 0, 30) }
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.statusCode)
        }
    }

    @Test
    fun `connection failure is reported as service unavailable`() {
        val search = ContentSearchRepository(object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse = throw java.io.IOException("unavailable")
        })
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows<ResponseStatusException> { search.search(SearchKind.ANIME, "검색", 0, 30) }.statusCode)
    }

    @Test
    fun `created documents and already deleted documents are accepted but rejected writes fail`() {
        repository(201, "{}").write(SearchKind.ANIME, "1", mapOf("id" to "1"))
        repository(404, "{}").write(SearchKind.ANIME, "1", null)
        assertThrows<IllegalStateException> { repository(400, "{}").write(SearchKind.ANIME, "1", mapOf("id" to "1")) }
    }

    @Test
    fun `invalid pages and oversized queries never reach Elasticsearch`() {
        val search = ContentSearchRepository(object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse = error("Unexpected request")
        })
        assertThrows<IllegalArgumentException> { search.search(SearchKind.ANIME, "검색", Int.MAX_VALUE, 30) }
        assertThrows<IllegalArgumentException> { search.search(SearchKind.ANIME, "검색", -1, 30) }
        assertThrows<IllegalArgumentException> { search.search(SearchKind.ANIME, "가".repeat(301), 0, 30) }
    }
}
