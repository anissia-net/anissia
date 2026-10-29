package anissia.search

import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import anissia.support.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class SearchSyncMariaDbTest {
    private lateinit var source: DriverManagerDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var manager: DataSourceTransactionManager
    private lateinit var tx: TransactionStatus
    private lateinit var queue: SearchSyncQueue
    private val id = UUID.randomUUID().toString().replace("-", "")

    @BeforeEach
    fun begin() {
        source = DriverManagerDataSource(System.getenv("ANISSIA_TEST_DB_URL"), System.getenv("ANISSIA_TEST_DB_USER"), System.getenv("ANISSIA_TEST_DB_PASSWORD"))
        jdbc = JdbcTemplate(source)
        manager = DataSourceTransactionManager(source)
        tx = manager.getTransaction(DefaultTransactionDefinition())
        queue = SearchSyncQueue(jdbc)
    }

    @AfterEach
    fun rollback() { if (::tx.isInitialized && !tx.isCompleted) manager.rollback(tx) }

    @Test
    fun `source rollback also rolls back its search work`() {
        queue.onChange(SearchChangedEvent(SearchKind.IMAGE, id))
        assertEquals(1L, count())
        manager.rollback(tx)
        assertEquals(0L, count())
    }

    @Test
    fun `completing an older work item cannot erase a concurrent newer change`() {
        queue.onChange(SearchChangedEvent(SearchKind.IMAGE, id))
        val old = SearchSyncTask(SearchKind.IMAGE, id, 1)
        queue.onChange(SearchChangedEvent(SearchKind.IMAGE, id))
        queue.complete(old)
        assertEquals(2L, jdbc.queryForObject("SELECT revision FROM search_sync_task WHERE kind='image' AND entity_id=?", Long::class.java, id))
        queue.complete(old.copy(revision = 2))
        assertEquals(0L, count())
    }

    @Test
    fun `failure stays queued and a new edit resets backoff without losing its revision`() {
        queue.onChange(SearchChangedEvent(SearchKind.IMAGE, id))
        queue.retry(SearchSyncTask(SearchKind.IMAGE, id, 1))
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM search_sync_task WHERE kind='image' AND entity_id=?", Int::class.java, id))
        queue.onChange(SearchChangedEvent(SearchKind.IMAGE, id))
        queue.retry(SearchSyncTask(SearchKind.IMAGE, id, 1))
        assertEquals(0, jdbc.queryForObject("SELECT attempts FROM search_sync_task WHERE kind='image' AND entity_id=?", Int::class.java, id))
    }

    @Test
    fun `a template change queues every current anime reference`() {
        val animeId = "${System.nanoTime()}"
        jdbc.update("INSERT INTO wiki_reference(owner_kind,owner_no,target_kind,target_id) VALUES ('anime',?,'template',?)", animeId.toLong(), id)
        queue.onChange(SearchChangedEvent(SearchKind.TEMPLATE, id))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM search_sync_task WHERE kind='anime' AND entity_id=?", Long::class.java, animeId))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM search_sync_task WHERE kind='template' AND entity_id=?", Long::class.java, id))
    }

    @Test
    fun `separate connections cannot rebuild or sync concurrently`() {
        val lock = SearchWorkLock(source)
        assertEquals(true, lock.run {
            assertNull(SearchWorkLock(source).run { true })
            true
        })
        assertEquals(true, lock.run { true })
    }

    @Test
    fun `both admin modes rebuild all four aliases and failure never switches them`() {
        for (reset in listOf(false, true)) {
            val fake = IndexClient()
            val service = service(fake)
            assertEquals(SearchKind.entries.map { it.key }.toSet(), service.rebuild(reset).keys)
            assertEquals(4, fake.created.size)
            assertEquals(8, Json.readTree(requireNotNull(fake.switched)).path("actions").size())
            if (!reset) fake.created.values.forEach { assertTrue(it.contains("previous_analyzer")) }
            else fake.created.values.forEach { assertTrue(it.contains("nori_tokenizer")) }
        }
        val broken = IndexClient(failCreation = true)
        assertThrows<IllegalStateException> { service(broken).rebuild(true) }
        assertNull(broken.switched)
    }

    private fun service(client: SearchClient) = SearchIndexService(client, mock(SearchDocuments::class.java), ContentSearchRepository(client), queue, SearchWorkLock(source), false)
    private fun count() = jdbc.queryForObject("SELECT COUNT(*) FROM search_sync_task WHERE kind='image' AND entity_id=?", Long::class.java, id)

    private class IndexClient(val failCreation: Boolean = false) : SearchClient {
        val created = linkedMapOf<String, String>()
        var switched: String? = null
        override fun request(method: String, endpoint: String, body: String?): SearchResponse {
            if (endpoint.startsWith("/_alias/")) return SearchResponse(200, """{"old_${endpoint.substringAfterLast('/')}":{}}""")
            if (endpoint == "/_aliases") { switched = body; return SearchResponse(200, """{"acknowledged":true}""") }
            if (method == "PUT") {
                if (failCreation && created.isNotEmpty()) return SearchResponse(400, "{}")
                created[endpoint] = requireNotNull(body)
            }
            if (method == "GET" && !endpoint.endsWith("/_count")) return SearchResponse(200, """{"previous":{"settings":{"index":{"analysis":{"analyzer":{"previous_analyzer":{"type":"standard"}}}}},"mappings":{"properties":{}}}}""")
            return SearchResponse(200, if (endpoint.endsWith("/_count")) """{"count":0}""" else "{}")
        }
    }
}
