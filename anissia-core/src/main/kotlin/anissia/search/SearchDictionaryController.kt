package anissia.search

import anissia.security.Actor
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.*

data class DictionaryRule(val id: String, val category: String, val term: String, val value: String)
data class RuleInput(val term: String, val value: String = "", val revision: Long)
data class DraftInput(val revision: Long)
data class VersionInput(val version: Long)
data class DictionaryTestInput(val q: String, val kind: String, val version: Long)

@RestController
@RequestMapping("/admin/search")
class SearchDictionaryController(
    private val dictionaries: SearchDictionaryService,
    private val inspector: DictionaryInspector,
) {
    @ModelAttribute
    fun authorize(actor: Actor, response: HttpServletResponse) {
        response.setHeader("Cache-Control", "no-store")
        actor.validateAdmin()
    }

    @GetMapping("/state")
    fun state() = ApiResponse.ok(dictionaries.state())

    @GetMapping("/rules/{category}")
    fun rules(@PathVariable category: String, @RequestParam(defaultValue = "") q: String,
              @RequestParam(defaultValue = "0") page: Int) = ApiResponse.ok(dictionaries.rules(category, q, page))

    @PostMapping("/rules/{category}")
    fun add(@PathVariable category: String, @RequestBody input: RuleInput, actor: Actor): ApiResponse<Unit> {
        dictionaries.add(category, input.term, input.value, input.revision, actor)
        return ApiResponse.ok()
    }

    @DeleteMapping("/rules/{category}/{id}")
    fun delete(@PathVariable category: String, @PathVariable id: String, @RequestParam revision: Long, actor: Actor): ApiResponse<Unit> {
        dictionaries.delete(category, id, revision, actor)
        return ApiResponse.ok()
    }

    @GetMapping("/analyze")
    fun analyze(@RequestParam q: String) = ApiResponse.ok(inspector.analyze(SearchKind.ANIME.alias, checkedQuery(q)))

    @PostMapping("/prepare")
    fun prepare(@RequestBody input: DraftInput, actor: Actor): ApiResponse<Unit> {
        dictionaries.prepare(input.revision, actor)
        return ApiResponse.ok()
    }

    @PostMapping("/test")
    fun test(@RequestBody input: DictionaryTestInput, actor: Actor): ApiResponse<Map<String, SearchInspection>> {
        val query = checkedQuery(input.q)
        val kind = requireNotNull(SearchKind.entries.find { it.key == input.kind }) { "검색 영역을 확인해 주세요." }
        val target = dictionaries.targets(input.version).getValue(kind)
        val active = inspector.inspect(kind, kind.alias, query)
        val candidate = inspector.inspect(kind, target, query)
        if (active.total > 0 || candidate.total > 0 || active.analysis != candidate.analysis) {
            dictionaries.markTested(input.version, query, kind, actor)
        }
        return ApiResponse.ok(mapOf("active" to active, "candidate" to candidate))
    }

    @PostMapping("/publish")
    fun publish(@RequestBody input: VersionInput, actor: Actor): ApiResponse<Unit> {
        dictionaries.publish(input.version, actor)
        return ApiResponse.ok()
    }

    private fun checkedQuery(q: String): String {
        require(q.isNotBlank() && q.length <= 300) { "검색어를 1~300자로 입력해 주세요." }
        return q.trim()
    }
}
