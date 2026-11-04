package anissia.search

import anissia.external.search.SearchClient
import anissia.support.Json
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.node.ObjectNode
import java.util.Locale

data class DictionaryTokens(val input: String, val corrected: String, val raw: List<String>, val tokens: List<String>)
data class DictionaryHit(val id: String, val title: String)
data class SearchInspection(val analysis: DictionaryTokens, val results: List<DictionaryHit>, val total: Long)

private val whitespace = Regex("\\s+")
private fun folded(value: String) = value.trim().lowercase(Locale.ROOT).replace(whitespace, " ")

object DictionaryAnalysis {
    fun validate(rule: DictionaryRule) {
        val term = rule.term
        val value = rule.value
        require(term.isNotBlank() && term.length <= 100 && value.length <= 500 &&
            term == term.trim() && value == value.trim() && (term + value).none(Char::isISOControl)) {
            "표제어는 1~100자, 값은 500자 이내로 입력해 주세요. 앞뒤 공백과 줄바꿈은 사용할 수 없습니다."
        }
        when (rule.category) {
            "user-dictionary" -> {
                require(term.none(Char::isWhitespace) && '#' !in term && '#' !in value) { "사용자 사전 표제어에는 공백이나 #을 넣을 수 없습니다." }
                require(value.isEmpty() || value.split(whitespace).joinToString("") == term) { "분해한 단어를 붙이면 원래 표제어와 같아야 합니다." }
            }
            "synonyms" -> {
                val variants = listOf(term) + value.split(',').map { it.trim() }
                require(variants.size in 2..20 && variants.all { it.isNotBlank() && it.length <= 100 && ',' !in it && '#' !in it && '\\' !in it && "=>" !in it } &&
                    variants.map(::folded).distinct().size == variants.size) { "쉼표로 구분해 서로 다른 동의어를 입력해 주세요. #, 역슬래시, =>는 사용할 수 없습니다." }
            }
            "corrections" -> require(value.isNotBlank() && value.length <= 100 && folded(term) != folded(value)) { "다른 교정 결과를 100자 이내로 입력해 주세요." }
            "stopwords" -> require(term.none(Char::isWhitespace) && value.isEmpty()) { "불용어에는 제외할 단어 하나만 입력해 주세요." }
            else -> throw IllegalArgumentException("올바르지 않은 사전 종류입니다.")
        }
    }

    fun definition(rules: List<DictionaryRule>): String {
        rules.forEach(::validate)
        require(rules.distinctBy { it.category to folded(it.term) }.size == rules.size) { "같은 종류에 중복된 표제어가 있습니다." }
        val root = Json.readTree(ContentSearchRepository.MAPPING) as ObjectNode
        val analysis = root.withObjectProperty("settings").withObjectProperty("analysis")
        val tokenizer = analysis.withObjectProperty("tokenizer")
        val users = rules.filter { it.category == "user-dictionary" }.map { listOf(it.term, it.value).filter(String::isNotEmpty).joinToString(" ") }
        val korean = tokenizer.withObjectProperty("korean")
        if (users.isNotEmpty()) korean.set("user_dictionary_rules", Json.mapper.valueToTree(users))

        val synonyms = rules.filter { it.category == "synonyms" }.map { it.term + ", " + it.value }
        val stops = rules.filter { it.category == "stopwords" }.map { folded(it.term) }
        val corrections = rules.filter { it.category == "corrections" }.associate { folded(it.term) to folded(it.value) }
        val mappings = root.withObjectProperty("mappings")
        val hasSearchFilters = synonyms.isNotEmpty() || stops.isNotEmpty()
        val analyzer = if (hasSearchFilters) "dictionary_search" else "search_text"
        if (hasSearchFilters) {
            val filters = analysis.withObjectProperty("filter")
            val chain = mutableListOf("nori_part_of_speech", "nori_readingform", "lowercase")
            var tokenizerName = "korean"
            if (synonyms.isNotEmpty()) {
                // Synonym rules require one path through a compound. Indexing still keeps both paths.
                tokenizerName = "korean_synonyms"
                tokenizer.set(tokenizerName, korean.deepCopy().put("decompound_mode", "discard"))
                filters.set("dictionary_synonyms", Json.mapper.valueToTree(mapOf("type" to "synonym_graph", "lenient" to false, "synonyms" to synonyms)))
                chain.add("dictionary_synonyms")
            }
            if (stops.isNotEmpty()) {
                filters.set("dictionary_stop", Json.mapper.valueToTree(mapOf("type" to "stop", "stopwords" to stops)))
                chain.add("dictionary_stop")
            }
            analysis.withObjectProperty("analyzer").set(analyzer, Json.mapper.valueToTree(mapOf("type" to "custom", "tokenizer" to tokenizerName, "filter" to chain)))
            for (field in mappings.path("properties")) if (field.path("type").asString() == "text") (field as ObjectNode).put("search_analyzer", analyzer)
        }
        mappings.withObjectProperty("_meta").set("anissia_query", Json.mapper.valueToTree(mapOf("analyzer" to analyzer, "corrections" to corrections, "stopwords" to stops)))
        return Json.write(root)
    }
}

internal data class DictionaryIndex(val name: String, val analyzer: String, val corrections: Map<String, String>, val stopwords: Set<String>) {
    private val maxWords = corrections.keys.maxOfOrNull { term -> term.count { it == ' ' } + 1 } ?: 0
    // Match whole words and phrases once, preferring the longest matching correction.
    fun correct(text: String): String {
        if (corrections.isEmpty() || text.isBlank()) return text
        val words = folded(text).split(' ')
        val output = mutableListOf<String>()
        var at = 0
        while (at < words.size) {
            var end = minOf(at + maxWords, words.size)
            var correction: String? = null
            while (end > at) {
                correction = corrections[words.subList(at, end).joinToString(" ")]
                if (correction != null) break
                end--
            }
            if (correction == null) output.add(words[at++]) else {
                output.add(correction)
                at = end
            }
        }
        return output.joinToString(" ")
    }
}

internal data class AnalyzedSearchText(val analysis: DictionaryTokens, val partialWords: List<String>)

internal class SearchAnalyzer(private val client: SearchClient) {
    private fun unavailable() = ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "검색엔진 분석 정보를 가져오지 못했습니다.")
    private fun request(method: String, path: String, body: String? = null) = try {
        val response = client.request(method, path, body)
        if (!response.isOk) throw unavailable()
        Json.readTree(response.body)
    } catch (e: java.io.IOException) { throw unavailable() }

    private val versions = Caffeine.newBuilder().maximumSize(32).build<String, DictionaryIndex>()

    fun resolve(target: String): DictionaryIndex {
        // Resolve the alias on each request. Cache rules only by immutable physical generation.
        val resolution = request("GET", "/$target/_mapping?filter_path=*.mappings.dynamic,*.mappings._meta.anissia_query.analyzer")
        val resolved = resolution.properties().singleOrNull()?.takeIf { it.value.has("mappings") } ?: throw unavailable()
        val analyzerName = resolved.value.path("mappings").path("_meta").path("anissia_query").path("analyzer").asString("")
        return versions.get(resolved.key) { name ->
            if (analyzerName.isEmpty()) return@get DictionaryIndex(name, "search_text", emptyMap(), emptySet())
            val mapping = request("GET", "/$name/_mapping?filter_path=*.mappings._meta.anissia_query")
            val meta = mapping.path(name).path("mappings").path("_meta").path("anissia_query")
            if (!meta.isObject) throw unavailable()
            DictionaryIndex(name, analyzerName, meta.path("corrections").properties().associate { it.key to it.value.asString() },
                meta.path("stopwords").toList().map { it.asString() }.toSet())
        }
    }

    fun analyze(index: DictionaryIndex, input: String, corrected: String): AnalyzedSearchText {
        if (corrected.isBlank()) return AnalyzedSearchText(DictionaryTokens(input, corrected, emptyList(), emptyList()), emptyList())
        val detail = request("POST", "/${index.name}/_analyze", Json.write(mapOf(
            "analyzer" to index.analyzer, "text" to corrected, "explain" to true, "attributes" to listOf("posType")
        ))).path("detail")
        val raw = detail.path("tokenizer").path("tokens")
        val stages = detail.path("tokenfilters").toList()
        if (!raw.isArray || stages.isEmpty() || !stages.last().path("tokens").isArray) throw unavailable()
        val final = stages.last().path("tokens")
        val words = Regex("\\S+").findAll(corrected).toList()
        val stopTokens = if (index.stopwords.isEmpty()) emptyList() else
            (raw.toList() + stages[stages.lastIndex - 1].path("tokens").toList()).filter { folded(it.path("token").asString()) in index.stopwords }

        // A whole removed word can be omitted from the keyword path. When Nori removes
        // only part of a word, the analyzed query alone can represent the remaining text.
        fun overlaps(token: tools.jackson.databind.JsonNode, word: MatchResult) =
            token.path("start_offset").asInt() <= word.range.last && token.path("end_offset").asInt() > word.range.first
        val partialWords = mutableListOf<String>()
        for (word in words) {
            if (stopTokens.none { overlaps(it, word) }) partialWords.add(word.value)
            else if (final.any { overlaps(it, word) }) {
                partialWords.clear()
                break
            }
        }
        return AnalyzedSearchText(DictionaryTokens(input, corrected, raw.toList().map { it.path("token").asString() },
            final.toList().map { it.path("token").asString() }), partialWords)
    }
}

@Service
class DictionaryInspector(private val repository: ContentSearchRepository) {
    fun analyze(target: String, q: String): DictionaryTokens {
        require(q.length <= 300) { "검색어는 300자 이내로 입력해 주세요." }
        val index = repository.analyzer.resolve(target)
        val kind = SearchKind.entries.firstOrNull { target == it.alias || target.startsWith(it.alias + "_") }
            ?: throw IllegalArgumentException("올바르지 않은 검색 대상입니다.")
        val text = searchTerms(kind, q).words.joinToString(" ")
        return repository.analyzer.analyze(index, q, index.correct(text)).analysis
    }

    fun inspect(kind: SearchKind, target: String, q: String): SearchInspection =
        repository.inspect(kind, target, q)
}
