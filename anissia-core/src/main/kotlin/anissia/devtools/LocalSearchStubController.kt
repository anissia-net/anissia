package anissia.devtools

import anissia.support.logger
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("local")
@RestController
class LocalSearchStubController {

    private val log = logger<LocalSearchStubController>()

    @RequestMapping("/local/fake/elasticsearch", produces = ["application/json"])
    fun fake(request: HttpServletRequest): String {
        log.info("Local Fake Elasticsearch: {} {}", request.method, request.requestURI)
        return EMPTY_SEARCH_RESULT
    }

    @RequestMapping("/local-mock/elasticsearch", produces = ["application/json"])
    fun mock(): String = EMPTY_SEARCH_RESULT

    companion object {
        private const val EMPTY_SEARCH_RESULT = """{"hits":{"total":{"value":0},"hits":[]}}"""
    }
}
