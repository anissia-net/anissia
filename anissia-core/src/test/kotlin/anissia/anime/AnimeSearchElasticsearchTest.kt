package anissia.anime

import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeStatus
import anissia.anime.repository.AnimeSearchRepository
import anissia.external.search.ElasticsearchClient
import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import anissia.support.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.LocalDate
import java.util.UUID

/**
 * Opt in with ANISSIA_TEST_ELASTICSEARCH_URL=http://127.0.0.1:9200 (Nori required).
 * All requests are confined to a new UUID index, which is removed after each test.
 */
@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_ELASTICSEARCH_URL", matches = "http://(127\\.0\\.0\\.1|localhost):[0-9]+/?")
class AnimeSearchElasticsearchTest {
    private val index = "anissia_nori_test_${UUID.randomUUID().toString().replace("-", "")}"
    private lateinit var client: ElasticsearchClient
    private lateinit var scopedClient: SearchClient
    private lateinit var repository: AnimeSearchRepository
    private var indexCreated = false
    private var indexMapping = ""

    @BeforeEach
    fun createIsolatedIndex() {
        client = ElasticsearchClient(requireNotNull(System.getenv("ANISSIA_TEST_ELASTICSEARCH_URL")), "", "")
        scopedClient = object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse {
                val prefix = "/anissia_anime"
                require(endpoint == prefix || endpoint.startsWith("$prefix/") || endpoint.startsWith("$prefix?")) {
                    "Test request must target only the anime index: $endpoint"
                }
                val response = client.request(method, "/$index" + endpoint.removePrefix(prefix), body)
                assertTrue(response.status in 200..299 || (method == "HEAD" && response.status == 404)) {
                    "$method $endpoint returned ${response.status}: ${response.body}"
                }
                if (endpoint == prefix && method == "PUT" && response.status in 200..299) {
                    indexCreated = true
                    indexMapping = requireNotNull(body)
                } else if (endpoint == prefix && method == "DELETE") {
                    indexCreated = false
                }
                return response
            }
        }
        repository = AnimeSearchRepository(scopedClient)
        repository.dropAndCreateIndex()
    }

    @AfterEach
    fun removeIsolatedIndex() {
        if (::client.isInitialized) {
            try {
                if (indexCreated) scopedClient.request("DELETE", "/anissia_anime")
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `Korean endings are analyzed when indexing and querying`() {
        save(1, "학교에서 만나는 친구")
        save(2, "바다 여행")
        refresh()

        assertEquals(setOf(1L), find("학교로 친구에게"))
        val response = scopedClient.request(
            "POST", "/anissia_anime/_search",
            """{"query":{"term":{"subject.nori":"학교"}}}""",
        )
        assertEquals(1L, Json.readTree(response.body).path("hits").path("total").path("value").asLong())
    }

    @Test
    fun `compound words can be found with separated morphemes`() {
        save(1, "한국어 형태소 분석")
        save(2, "바다 여행")
        refresh()

        assertEquals(setOf(1L), find("한국 어 형태 소"))
    }

    @Test
    fun `partial title search retains AND semantics`() {
        save(1, "카우보이비밥")
        save(2, "카우보이 이야기")
        save(3, "비밥 이야기")
        refresh()

        assertEquals(setOf(1L), find("비밥 카우"))
        assertTrue(find("비밥 카우 우주선").isEmpty())
    }

    @Test
    fun `original titles remain searchable in Latin and Japanese`() {
        save(1, "카우보이 비밥", originalSubject = "Cowboy Bebop")
        save(2, "이웃집 토토로", originalSubject = "となりのトトロ")
        refresh()

        assertEquals(setOf(1L), find("COWBOY bebop"))
        assertEquals(setOf(2L), find("となりのトトロ"))
    }

    @Test
    fun `genre AND and translator OR filters also constrain Nori matches`() {
        save(1, "학교의 친구", genres = "판타지,모험", translators = listOf("철수"))
        save(2, "학교의 친구들", genres = "판타지,모험", translators = listOf("영희"))
        save(3, "학교의 친구", genres = "판타지", translators = listOf("철수"))
        save(4, "학교의 친구", genres = "판타지,모험", translators = listOf("민수"))
        save(5, "바다 이야기", genres = "판타지,모험", translators = listOf("철수"))
        refresh()

        assertEquals(setOf(1L, 2L), find("학교에서 친구에게 #판타지 #모험 @철수 @영희"))
        assertEquals(setOf(1L, 2L, 5L), find("#판타지 #모험 @철수 @영희"))
    }

    @Test
    fun `completed titles retain date ordering and thirty item pagination`() {
        for (id in 1L..35L) {
            save(id, "페이지 작품 $id", status = AnimeStatus.END, endDate = LocalDate.of(2026, 1, 1).plusDays(id).toString())
        }
        save(100, "페이지 방영작", status = AnimeStatus.ON, endDate = "2027-01-01")
        refresh()

        val first = repository.search("페이지 /완결", 0)
        val second = repository.search("페이지 /완결", 1)
        assertEquals(35L, first.totalHits)
        assertEquals((35L downTo 6L).toList(), first.animeNos)
        assertEquals((5L downTo 1L).toList(), second.animeNos)
        assertEquals(first.animeNos, repository.search("/완결", 0).animeNos)
    }

    @Test
    fun `updated titles replace old Nori terms and deletion removes search results`() {
        save(1, "학교에서 만나는 친구")
        refresh()
        assertEquals(setOf(1L), find("학교로 친구에게"))

        save(1, "바다에서 만나는 고래")
        refresh()
        assertTrue(find("학교로 친구에게").isEmpty())
        assertEquals(setOf(1L), find("바다로 고래에게"))

        repository.deleteByAnimeNo(1)
        refresh()
        assertTrue(find("바다로 고래에게").isEmpty())
    }

    @Test
    fun `existing documents gain Nori terms after additive mapping and backfill`() {
        val subjectMapping = Json.readTree(indexMapping).path("mappings").path("properties").path("subject")
        scopedClient.request("DELETE", "/anissia_anime")
        scopedClient.request("PUT", "/anissia_anime", """{"mappings":{"properties":{"subject":{"type":"text"}}}}""")
        scopedClient.request("PUT", "/anissia_anime/_doc/1", """{"animeNo":1,"subject":"학교에서 만나는 친구"}""")
        refresh()

        scopedClient.request(
            "PUT", "/anissia_anime/_mapping",
            Json.write(mapOf("properties" to mapOf("subject" to subjectMapping))),
        )
        assertTrue(find("학교로 친구에게").isEmpty())
        val backfill = scopedClient.request("POST", "/anissia_anime/_update_by_query?refresh=true", """{"query":{"match_all":{}}}""")
        val result = Json.readTree(backfill.body)
        assertEquals(1L, result.path("updated").asLong())
        assertEquals(0, result.path("failures").size())
        assertEquals(setOf(1L), find("학교로 친구에게"))
        assertEquals(1L, Json.readTree(scopedClient.request("GET", "/anissia_anime/_count").body).path("count").asLong())
    }

    private fun save(
        id: Long,
        subject: String,
        originalSubject: String = "",
        genres: String = "",
        translators: List<String> = emptyList(),
        status: AnimeStatus = AnimeStatus.ON,
        endDate: String = "",
    ) {
        repository.update(
            Anime(animeNo = id, subject = subject, originalSubject = originalSubject, genres = genres, status = status, endDate = endDate),
            translators,
        )
    }

    private fun refresh() {
        scopedClient.request("POST", "/anissia_anime/_refresh")
    }

    private fun find(query: String): Set<Long> = repository.search(query, 0).animeNos.toSet()
}
