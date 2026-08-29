package anissia.external.search

import anissia.support.logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["anissia.search.mock"], havingValue = "true")
class LocalSearchClient : SearchClient {

    private val log = logger<LocalSearchClient>()

    override fun request(method: String, endpoint: String, body: String?): SearchResponse {
        log.info("Local Mock Elasticsearch: {} {}{}", method, endpoint, body?.let { "\n$it" } ?: "")
        return SearchResponse(status = 200, body = EMPTY_SEARCH_RESULT)
    }

    companion object {
        private const val EMPTY_SEARCH_RESULT = """{"hits":{"total":{"value":0},"hits":[]}}"""
    }
}
