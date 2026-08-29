package anissia.anime.repository

import anissia.anime.domain.Anime
import anissia.external.search.SearchClient
import anissia.support.Json
import anissia.support.logger
import org.springframework.stereotype.Repository
import java.util.Locale

class AnimeSearchResult(
    val animeNos: List<Long>,
    val totalHits: Long,
)

@Repository
class AnimeSearchRepository(
    private val searchClient: SearchClient,
) {
    private val log = logger<AnimeSearchRepository>()

    fun search(query: String, page: Int): AnimeSearchResult {
        val keywords = ArrayList<String>()
        val genres = ArrayList<String>()
        val translators = ArrayList<String>()
        val end = query.indexOf("/완결") != -1

        query.lowercase(Locale.getDefault())
            .split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "/완결" }
            .forEach { word ->
                when {
                    word[0] == '#' && word.length > 1 -> genres.add(word.substring(1))
                    word[0] == '@' && word.length > 1 -> translators.add(word.substring(1))
                    else -> keywords.add(word)
                }
            }

        val request = Json.write(
            Json.objectNode().apply {
                put("_source", false)
                putObject("query").apply {
                    putObject("bool").apply {
                        put("minimum_should_match", "100%")

                        if (keywords.isNotEmpty() || genres.isNotEmpty()) {
                            putArray("must").apply {
                                keywords.forEach {
                                    addObject().apply { putObject("wildcard").apply { put("subject", "*$it*") } }
                                }
                                genres.forEach {
                                    addObject().apply { putObject("match").apply { put("genres", it) } }
                                }
                            }
                        }

                        if (translators.isNotEmpty() || end) {
                            putArray("filter").apply {
                                if (translators.isNotEmpty()) {
                                    addObject().apply {
                                        putObject("bool").apply {
                                            putArray("should").apply {
                                                translators.forEach {
                                                    addObject().apply {
                                                        putObject("match").apply { put("translators", it) }
                                                    }
                                                }
                                            }
                                            put("minimum_should_match", "1")
                                        }
                                    }
                                }
                                if (end) {
                                    addObject().apply { putObject("match").apply { put("status", "END") } }
                                }
                            }
                        }
                    }
                }
                if (end) {
                    putArray("sort").apply {
                        addObject().apply { putObject("endDate").apply { put("order", "desc") } }
                    }
                }
                put("from", page * 30)
                put("size", 30)
            },
        )

        log.info("anime search {}: {} {} {} {}", query, keywords, genres, translators, end)

        val hits = Json.readTree(searchClient.request("POST", "/$INDEX/_search", request).body).path("hits")

        return AnimeSearchResult(
            animeNos = hits.path("hits").valueStream()
                .map { it.path("_id").asString("0").toLongOrNull() ?: 0L }
                .toList(),
            totalHits = hits.path("total").path("value").asLong(0),
        )
    }

    fun update(anime: Anime, translators: List<String>) {
        val body = Json.write(
            mapOf(
                "animeNo" to anime.animeNo,
                "week" to anime.week,
                "subject" to anime.subject + " " + anime.originalSubject,
                "status" to anime.status.name,
                "genres" to anime.genres.split(",".toRegex()),
                "translators" to translators,
                "endDate" to anime.endDate.replace("-", "").run { if (isEmpty()) 0L else toLong() },
            ),
        )
        val response = searchClient.request("PUT", "/$INDEX/_doc/${anime.animeNo}", body)
        log.info("Updated anime document: {} / {}", body, response.status)
    }

    fun deleteByAnimeNo(animeNo: Long) {
        searchClient.request("DELETE", "/$INDEX/_doc/$animeNo")
    }

    fun dropAndCreateIndex() {
        searchClient.deleteIndexIfExists(INDEX)
        log.info("Dropped index: {}", INDEX)
        searchClient.createIndex(INDEX, INDEX_MAPPING)
        log.info("Created index: {}", INDEX)
    }

    companion object {
        private const val INDEX = "anissia_anime"
        private val INDEX_MAPPING = """{"mappings":{"properties": {
            "animeNo": {"type": "long","store": true},
            "week": {"type": "keyword"},
            "subject": {"type": "text"},
            "genres": {"type": "keyword"},
            "status": {"type": "keyword"},
            "translators": {"type": "keyword"},
            "endDate": {"type": "long"}
        }}}""".trimIndent()
    }
}
