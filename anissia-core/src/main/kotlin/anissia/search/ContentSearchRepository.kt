package anissia.search

import anissia.external.search.SearchClient
import anissia.support.Json
import anissia.support.logger
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.util.Locale

enum class SearchKind(val key: String, val table: String, val idColumn: String) {
    ANIME("anime", "anime", "anime_no"), BOARD("board", "board_topic", "topic_no"),
    TEMPLATE("template", "wiki_template", "template_no"), IMAGE("image", "wiki_image", "id");
    val alias: String get() = "anissia_search_$key"
}

data class ContentSearchResult(val ids: List<String>, val total: Long)

@Repository
class ContentSearchRepository(private val client: SearchClient) {
    private val log = logger<ContentSearchRepository>()

    fun search(kind: SearchKind, query: String, page: Int, size: Int, filters: Map<String, Any> = emptyMap()): ContentSearchResult {
        require(query.length <= 300 && page >= 0 && size in 1..100 && (page.toLong() + 1) * size <= 10_000) {
            "검색어는 300자 이내이며 최대 10,000번째 결과까지 조회할 수 있습니다."
        }
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        val genres = if (kind == SearchKind.ANIME) terms.filter { it.startsWith('#') && it.length > 1 }.map { it.drop(1) } else emptyList()
        val translators = if (kind == SearchKind.ANIME) terms.filter { it.startsWith('@') && it.length > 1 }.map { it.drop(1) } else emptyList()
        val ended = kind == SearchKind.ANIME && "/완결" in terms
        val words = if (kind == SearchKind.ANIME) terms.filterNot { it == "/완결" || it.length > 1 && (it.startsWith('#') || it.startsWith('@')) } else terms
        val constraints = filters.map { (key, value) -> if (value is Collection<*>) mapOf("terms" to mapOf(key to value)) else mapOf("term" to mapOf(key to value)) }.toMutableList()
        genres.forEach { constraints.add(mapOf("term" to mapOf("genres" to it))) }
        if (translators.isNotEmpty()) constraints.add(mapOf("terms" to mapOf("translators" to translators)))
        if (ended) constraints.add(mapOf("term" to mapOf("status" to "END")))
        val bool = mutableMapOf<String, Any>("filter" to constraints)
        if (words.isNotEmpty()) bool["must"] = listOf(textQuery(words, kind))
        val sort = if (ended && words.isEmpty()) listOf(mapOf("endDate" to "desc"), mapOf("id" to "asc"))
            else listOf(mapOf("_score" to "desc")) + (if (ended) listOf(mapOf("endDate" to "desc")) else emptyList()) + listOf(mapOf("id" to "asc"))
        val response = try {
            client.request("POST", "/${kind.alias}/_search", Json.write(mapOf(
                "_source" to false, "track_total_hits" to true, "query" to mapOf("bool" to bool),
                "sort" to sort, "from" to page * size, "size" to size,
            )))
        } catch (e: java.io.IOException) {
            log.error("search connection failed type={}", kind.key)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "검색엔진에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.", e)
        }
        if (response.status !in 200..299) {
            log.error("search failed type={} status={}", kind.key, response.status)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "검색엔진을 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.")
        }
        val result = Json.readTree(response.body)
        if (result.path("timed_out").asBoolean(false) || result.path("_shards").path("failed").asInt(0) > 0) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "검색을 완료하지 못했습니다. 다시 시도해 주세요.")
        }
        val hits = result.path("hits")
        val found = ContentSearchResult(hits.path("hits").valueStream().map { it.path("_id").asString() }.toList(), hits.path("total").path("value").asLong())
        log.info("search type={} query={} results={}", kind.key, Json.write(query), found.total)
        return found
    }

    private fun textQuery(words: List<String>, kind: SearchKind): Map<String, Any> {
        val text = words.joinToString(" ")
        fun combined(fields: List<String>) = mapOf("combined_fields" to mapOf("query" to text, "fields" to fields, "operator" to "and"))
        fun either(queries: List<Map<String, Any>>) = mapOf("bool" to mapOf("minimum_should_match" to 1, "should" to queries))
        val titles = listOf("title", "originalTitle")
        val partial = mapOf("bool" to mapOf("must" to words.map { word ->
            val escaped = word.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?")
            either(titles.map { mapOf("wildcard" to mapOf("$it.partial" to mapOf("value" to "*$escaped*"))) })
        }))
        val titleMatch = combined(listOf("title^8", "originalTitle^6"))
        val phrase = either(titles.map { mapOf("match_phrase" to mapOf(it to mapOf("query" to text))) })
        val all = either(listOf(combined(listOf("title^8", "originalTitle^6", "info^3", "body", "comments")), partial))
        val tiers = mutableListOf<Map<String, Any>>(
            mapOf("filter" to either(titles.map { mapOf("term" to mapOf("$it.partial" to text)) }), "weight" to 500),
            mapOf("filter" to phrase, "weight" to 400),
            mapOf("filter" to titleMatch, "weight" to 300),
            mapOf("filter" to partial, "weight" to 250),
        )
        if (kind == SearchKind.ANIME) tiers.add(mapOf("filter" to combined(listOf("title", "originalTitle", "info")), "weight" to 200))
        tiers.add(mapOf("filter" to mapOf("match_all" to emptyMap<String, Any>()), "weight" to 100))
        return mapOf("function_score" to mapOf(
            "query" to mapOf("script_score" to mapOf("query" to all, "script" to mapOf("source" to "_score / (1.0 + _score)"))),
            "functions" to tiers, "score_mode" to "max", "boost_mode" to "sum",
        ))
    }

    fun write(kind: SearchKind, id: String, document: Map<String, Any>?, target: String = kind.alias) {
        val response = if (document == null) client.request("DELETE", "/$target/_doc/$id")
            else client.request("PUT", "/$target/_doc/$id", Json.write(document))
        check(response.status in 200..299 || document == null && response.status == 404) { "Search write failed: ${kind.key}/$id status=${response.status}" }
    }

    fun writeBatch(kind: SearchKind, documents: Map<String, Map<String, Any>>, target: String) {
        val body = StringBuilder()
        var bytes = 0
        var count = 0
        fun flush() {
            if (count == 0) return
            val response = client.request("POST", "/$target/_bulk", body.toString())
            check(response.status in 200..299) { "Search bulk failed: ${kind.key} status=${response.status}" }
            val result = Json.readTree(response.body)
            val items = result.path("items")
            check(items.size() == count && !result.path("errors").asBoolean(true) && items.all {
                it.path("index").path("status").asInt() in 200..299
            }) { "Search bulk contained failed or missing documents: ${kind.key}" }
            body.setLength(0)
            bytes = 0
            count = 0
        }
        documents.forEach { (id, document) ->
            val entry = Json.write(mapOf("index" to mapOf("_id" to id))) + "\n" + Json.write(document) + "\n"
            val length = entry.toByteArray().size
            if (bytes + length > 8 * 1024 * 1024) flush()
            body.append(entry)
            bytes += length
            count++
        }
        flush()
    }

    companion object {
        val MAPPING: String = requireNotNull(ContentSearchRepository::class.java.getResource("/elasticsearch/search-index.json")).readText()
    }
}
