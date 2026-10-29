package anissia.anime

import anissia.external.search.ElasticsearchClient
import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import anissia.search.ContentSearchRepository
import anissia.search.SearchKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_ELASTICSEARCH_URL", matches = "http://(127\\.0\\.0\\.1|localhost):[0-9]+/?")
class AnimeSearchElasticsearchTest {
    private val index = "anissia_search_test_${UUID.randomUUID().toString().replace("-", "")}"
    private lateinit var client: ElasticsearchClient
    private lateinit var repository: ContentSearchRepository

    @BeforeEach
    fun create() {
        client = ElasticsearchClient(requireNotNull(System.getenv("ANISSIA_TEST_ELASTICSEARCH_URL")), "", "")
        val response = client.request("PUT", "/$index", ContentSearchRepository.MAPPING)
        assertEquals(200, response.status, response.body)
        repository = ContentSearchRepository(object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse {
                val prefix = SearchKind.entries.map { "/${it.alias}" }.first { endpoint.startsWith("$it/") }
                val result = client.request(method, "/$index" + endpoint.removePrefix(prefix), body)
                assertTrue(result.status in 200..299, result.body)
                return result
            }
        })
    }

    @AfterEach
    fun clean() {
        if (::client.isInitialized) try { client.request("DELETE", "/$index") } finally { client.close() }
    }

    private fun save(id: Int, title: String, body: String = "", info: String = "", original: String = "", extra: Map<String, Any> = emptyMap()) {
        repository.write(SearchKind.ANIME, id.toString(), mapOf("id" to id.toString(), "title" to title, "originalTitle" to original, "body" to body, "info" to info, "status" to "ON", "endDate" to 0) + extra)
    }
    private fun refresh() { assertEquals(200, client.request("POST", "/$index/_refresh").status) }
    private fun find(q: String) = repository.search(SearchKind.ANIME, q, 0, 30).ids

    @Test
    fun `exact title then phrase then partial title then anime info then wiki regardless of frequency`() {
        save(1, "바다", body = "짧은 본문")
        save(2, "바다 여행")
        save(3, "바다코끼리")
        save(4, "우주 여행", info = "바다")
        save(5, "학교 이야기", body = "바다 ".repeat(1000))
        refresh()
        assertEquals(listOf("1", "2", "3", "4", "5"), find("바다"))
    }

    @Test
    fun `terms may span title info and wiki but every term must match`() {
        save(1, "학교의 친구", body = "우주 탐험", info = "판타지")
        save(2, "학교의 친구", body = "바다 탐험")
        refresh()
        assertEquals(listOf("1"), find("학교에서 우주로"))
        assertEquals(listOf("1"), find("학교 판타지 우주"))
        assertTrue(find("학교 우주 고래").isEmpty())
    }

    @Test
    fun `Nori particles and compound words work during indexing and search`() {
        save(1, "한국어 형태소 분석", body = "학교에서 만나는 친구")
        refresh()
        assertEquals(listOf("1"), find("학교로 친구에게"))
        assertEquals(listOf("1"), find("한국 어 형태 소"))
    }

    @Test
    fun `partial original Latin and Japanese titles remain searchable`() {
        save(1, "카우보이비밥", original = "Cowboy Bebop")
        save(2, "이웃집 토토로", original = "となりのトトロ")
        refresh()
        assertEquals(listOf("1"), find("비밥 카우"))
        assertEquals(listOf("1"), find("COWBOY bebop"))
        assertEquals(listOf("2"), find("となりのトトロ"))
        assertTrue(find("비밥 카우 우주선").isEmpty())
    }

    @Test
    fun `genre AND translator OR and status constrain all fields`() {
        save(1, "학교", info = "판타지", extra = mapOf("genres" to listOf("판타지", "모험"), "translators" to listOf("철수"), "status" to "END"))
        save(2, "친구", body = "학교", extra = mapOf("genres" to listOf("판타지", "모험"), "translators" to listOf("영희"), "status" to "END"))
        save(3, "학교", extra = mapOf("genres" to listOf("판타지"), "translators" to listOf("철수")))
        refresh()
        assertEquals(listOf("1", "2"), find("학교 #판타지 #모험 @철수 @영희 /완결"))
    }

    @Test
    fun `completed keyword search prioritizes title while filter-only search orders by end date`() {
        save(1, "바다", extra = mapOf("status" to "END", "endDate" to 20260101))
        save(2, "우주", body = "바다", extra = mapOf("status" to "END", "endDate" to 20261231))
        refresh()
        assertEquals(listOf("1", "2"), find("바다 /완결"))
        assertEquals(listOf("2", "1"), find("/완결"))
    }

    @Test
    fun `pagination reports total and a stable remainder`() {
        for (id in 1..35) save(id, "페이지 $id")
        refresh()
        val first = repository.search(SearchKind.ANIME, "페이지", 0, 30)
        val second = repository.search(SearchKind.ANIME, "페이지", 1, 30)
        assertEquals(35L, first.total)
        assertEquals(30, first.ids.size)
        assertEquals(5, second.ids.size)
        assertEquals(35, (first.ids + second.ids).toSet().size)
    }

    @Test
    fun `updates remove old terms and deletes remove the whole result`() {
        save(1, "학교", body = "고래")
        refresh()
        assertEquals(listOf("1"), find("고래"))
        save(1, "학교", body = "우주")
        refresh()
        assertTrue(find("고래").isEmpty())
        assertEquals(listOf("1"), find("우주"))
        repository.write(SearchKind.ANIME, "1", null)
        refresh()
        assertTrue(find("우주").isEmpty())
    }

    @Test
    fun `board scope and comment text and private resource filters are enforced`() {
        save(1, "게시글", extra = mapOf("ticker" to "notice", "comments" to "학교 친구"))
        save(2, "게시글", extra = mapOf("ticker" to "inquiry", "comments" to "학교 친구"))
        save(3, "학교 자료", extra = mapOf("status" to "READY"))
        save(4, "학교 자료", extra = mapOf("status" to "DELETED"))
        refresh()
        assertEquals(listOf("1"), repository.search(SearchKind.BOARD, "학교에서 친구에게", 0, 20, mapOf("ticker" to "notice")).ids)
        for (kind in listOf(SearchKind.TEMPLATE, SearchKind.IMAGE)) {
            assertEquals(listOf("3"), repository.search(kind, "학교", 0, 24, mapOf("status" to listOf("READY"))).ids)
            assertEquals(listOf("4"), repository.search(kind, "학교", 0, 24, mapOf("status" to listOf("DELETED"))).ids)
        }
    }

    @Test
    fun `literal wildcard input cannot match every title`() {
        save(1, "일반 제목")
        save(2, "별*표")
        refresh()
        assertEquals(listOf("2"), find("*"))
    }
}
