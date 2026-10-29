package anissia.search

import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import anissia.support.Json
import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.ArgumentMatchers.any
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
            assertEquals(4, fake.restored.size)
            fake.created.values.forEach { definition ->
                assertEquals("-1", Json.readTree(definition).path("settings").path("refresh_interval").asString())
                assertEquals(0, Json.readTree(definition).path("settings").path("number_of_replicas").asInt())
            }
            fake.restored.values.forEach { restored ->
                val settings = Json.readTree(restored).path("index")
                if (reset) assertTrue(settings.path("refresh_interval").isNull)
                else { assertEquals("30s", settings.path("refresh_interval").asString()); assertEquals(2, settings.path("number_of_replicas").asInt()) }
            }
            assertEquals(8, Json.readTree(requireNotNull(fake.switched)).path("actions").size())
            if (!reset) fake.created.values.forEach { assertTrue(it.contains("previous_analyzer")) }
            else fake.created.values.forEach { assertTrue(it.contains("nori_tokenizer")) }
        }
        val broken = IndexClient(failCreation = true)
        assertThrows<IllegalStateException> { service(broken).rebuild(true) }
        assertNull(broken.switched)
    }

    @Test
    fun `bulk item failure leaves existing aliases untouched`() {
        val documents = mock(SearchDocuments::class.java)
        `when`(documents.ids(SearchKind.ANIME, "0", 100)).thenReturn(listOf("1"))
        `when`(documents.loadBatch(SearchKind.ANIME, listOf("1"))).thenReturn(mapOf("1" to mapOf("title" to "작품")))
        val client = IndexClient(failBulk = true)
        assertThrows<IllegalStateException> { service(client, documents).rebuild(false) }
        assertNull(client.switched)
    }

    @Test
    fun `search progress commits before the surrounding command finishes`() {
        val repository = mock(ActivePanelRepository::class.java)
        doAnswer { invocation ->
            val row = invocation.getArgument<ActivePanel>(0)
            jdbc.update("INSERT INTO active_panel(published,code,status,an,data1,reg_dt) VALUES(false,'TEXT','',0,?,CURRENT_TIMESTAMP)", row.data1)
            row
        }.`when`(repository).save(any(ActivePanel::class.java))
        val proxy = ProxyFactory(ActivePanelLogService(repository)).apply {
            addAdvice(TransactionInterceptor(manager, AnnotationTransactionAttributeSource()))
        }.proxy as ActivePanelLogService
        val marker = "search-progress-$id"
        try {
            proxy.addSearchProgress(marker, Actor())
            source.connection.use { connection ->
                connection.prepareStatement("SELECT COUNT(*) FROM active_panel WHERE data1 = ?").use { statement ->
                    statement.setString(1, marker)
                    statement.executeQuery().use { result -> result.next(); assertEquals(1, result.getInt(1)) }
                }
            }
            manager.rollback(tx)
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM active_panel WHERE data1 = ?", Int::class.java, marker))
        } finally {
            source.connection.use { connection -> connection.prepareStatement("DELETE FROM active_panel WHERE data1 = ?").use { statement -> statement.setString(1, marker); statement.executeUpdate() } }
        }
    }

    private fun service(client: SearchClient, documents: SearchDocuments = mock(SearchDocuments::class.java)) = SearchIndexService(client, documents, ContentSearchRepository(client), queue, SearchWorkLock(source), false)
    private fun count() = jdbc.queryForObject("SELECT COUNT(*) FROM search_sync_task WHERE kind='image' AND entity_id=?", Long::class.java, id)

    private class IndexClient(val failCreation: Boolean = false, val failBulk: Boolean = false) : SearchClient {
        val created = linkedMapOf<String, String>()
        val restored = linkedMapOf<String, String>()
        var switched: String? = null
        override fun request(method: String, endpoint: String, body: String?): SearchResponse {
            if (endpoint.startsWith("/_alias/")) return SearchResponse(200, """{"old_${endpoint.substringAfterLast('/')}":{}}""")
            if (endpoint == "/_aliases") { switched = body; return SearchResponse(200, """{"acknowledged":true}""") }
            if (endpoint.endsWith("/_bulk") && failBulk) return SearchResponse(200, """{"errors":true,"items":[{"index":{"status":400}}]}""")
            if (method == "PUT" && endpoint.endsWith("/_settings")) {
                restored[endpoint] = requireNotNull(body)
                return SearchResponse(200, "{}")
            }
            if (method == "PUT") {
                if (failCreation && created.isNotEmpty()) return SearchResponse(400, "{}")
                created[endpoint] = requireNotNull(body)
            }
            if (method == "GET" && !endpoint.endsWith("/_count")) return SearchResponse(200, """{"previous":{"settings":{"index":{"refresh_interval":"30s","number_of_replicas":"2","analysis":{"analyzer":{"previous_analyzer":{"type":"standard"}}}}},"mappings":{"properties":{}}}}""")
            return SearchResponse(200, if (endpoint.endsWith("/_count")) """{"count":0}""" else "{}")
        }
    }
}
