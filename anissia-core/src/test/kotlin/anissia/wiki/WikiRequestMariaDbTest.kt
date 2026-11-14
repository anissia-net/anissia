package anissia.wiki

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.account.repository.AccountRepository
import anissia.anime.repository.AnimeRepository
import anissia.search.ContentSearchRepository
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class WikiRequestMariaDbTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var transaction: TransactionTemplate
    private lateinit var jdbc: JdbcTemplate
    private lateinit var service: WikiRequestService
    private lateinit var images: WikiImageService
    private var accountNo = 0L
    private var requestNo = 0L
    private var title = ""
    private val body = WikiBody(blocks = Json.readTree("""[{"w":"p","ch":["승인할 본문"]}]"""))

    @BeforeEach
    fun prepareLocalFixtures() {
        val source = DriverManagerDataSource(System.getenv("ANISSIA_TEST_DB_URL"),
            requireNotNull(System.getenv("ANISSIA_TEST_DB_USER")), requireNotNull(System.getenv("ANISSIA_TEST_DB_PASSWORD")))
        jdbc = JdbcTemplate(source)
        factory = LocalContainerEntityManagerFactoryBean().apply {
            dataSource = source
            setPackagesToScan("anissia")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf(
                "hibernate.hbm2ddl.auto" to "none",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                "hibernate.timezone.default_storage" to "NORMALIZE",
            ))
            afterPropertiesSet()
        }
        val persistence = requireNotNull(factory.`object`)
        val entityManager = SharedEntityManagerCreator.createSharedEntityManager(persistence)
        val repositories = JpaRepositoryFactory(entityManager)
        val accounts = repositories.getRepository(AccountRepository::class.java)
        val manager = JpaTransactionManager(persistence).apply { dataSource = source; afterPropertiesSet() }
        transaction = TransactionTemplate(manager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            timeout = 15
        }
        val access = WikiAccessService(accounts)
        val templates = repositories.getRepository(WikiTemplateRepository::class.java)
        images = mock(WikiImageService::class.java)
        val content = WikiContentService(access, repositories.getRepository(AnimeRepository::class.java),
            repositories.getRepository(AnimeWikiRepository::class.java), templates,
            repositories.getRepository(WikiRevisionRepository::class.java), repositories.getRepository(WikiReferenceRepository::class.java),
            images, mock(ContentSearchRepository::class.java), mock(ApplicationEventPublisher::class.java))
        service = WikiRequestService(access, repositories.getRepository(WikiRequestRepository::class.java), templates, content, images)
        val unique = UUID.randomUUID().toString()
        title = "wiki-request-$unique"
        tx {
            accountNo = accounts.saveAndFlush(Account(email = "$unique@request.invalid", name = "req-${unique.take(8)}",
                password = "not-a-login-password", regDt = OffsetDateTime.now(), roles = mutableSetOf(AccountRole.TRANSLATOR))).an
            requestNo = service.submit(WikiRequestSubmission(WikiRequestKind.TEMPLATE_CREATE, title, body, "동시 승인 검증"),
                Actor(an = accountNo)).id
        }
    }

    @AfterEach
    fun removeOnlyCreatedFixtures() {
        try {
            if (::transaction.isInitialized && accountNo > 0) tx {
                jdbc.update("DELETE FROM wiki_request WHERE requester_an = ?", accountNo)
                val templateIds = jdbc.query("SELECT template_no FROM wiki_template WHERE title = ?", { rs, _ -> rs.getLong(1) }, title)
                templateIds.forEach { id ->
                    jdbc.update("DELETE FROM wiki_reference WHERE owner_kind = 'template' AND owner_no = ?", id)
                    jdbc.update("DELETE FROM wiki_revision WHERE kind = 'template' AND target_no = ?", id)
                    jdbc.update("DELETE FROM wiki_template WHERE template_no = ?", id)
                }
                jdbc.update("DELETE FROM account_role WHERE an = ?", accountNo)
                jdbc.update("DELETE FROM account WHERE an = ?", accountNo)
            }
        } finally {
            if (::factory.isInitialized) factory.destroy()
        }
    }

    @Test
    fun `simultaneous approvals create one template and one recorded decision`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val approvals = (1..2).map { executor.submit<String> {
                ready.countDown()
                assertTrue(start.await(10, TimeUnit.SECONDS))
                try {
                    tx { service.approve(requestNo, WikiRequestDecision("검토 완료"), Actor(an = accountNo)) }
                    "approved"
                } catch (error: ResponseStatusException) {
                    assertEquals(HttpStatus.CONFLICT, error.statusCode)
                    "conflict"
                }
            } }
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)) } finally { start.countDown() }
            assertEquals(listOf("approved", "conflict"), approvals.map { it.get(20, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_template WHERE title = ?", Long::class.java, title))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_revision WHERE kind = 'template' AND title = ?", Long::class.java, title))
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM wiki_request WHERE id = ?", String::class.java, requestNo))
        assertEquals(accountNo, jdbc.queryForObject("SELECT processed_an FROM wiki_request WHERE id = ?", Long::class.java, requestNo))
    }

    @Test
    fun `failure after a template insert rolls back the resource and the request decision together`() {
        doThrow(FailException("이미지를 더 이상 사용할 수 없습니다.")).`when`(images).requireReadyUrls(org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong())
        assertThrows<FailException> {
            tx { service.approve(requestNo, WikiRequestDecision("검토 완료"), Actor(an = accountNo)) }
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_template WHERE title = ?", Long::class.java, title))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_revision WHERE kind = 'template' AND title = ?", Long::class.java, title))
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM wiki_request WHERE id = ?", String::class.java, requestNo))
        assertNull(jdbc.queryForObject("SELECT processed_at FROM wiki_request WHERE id = ?", String::class.java, requestNo))
    }

    @Test
    fun `approval racing requester cancellation commits exactly one terminal transition`() {
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val actions = listOf<() -> Unit>(
                { service.approve(requestNo, WikiRequestDecision("검토 완료"), Actor(an = accountNo)) },
                { service.cancel(requestNo, Actor(an = accountNo)) },
            ).map { action -> executor.submit<String> {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                try {
                    tx { action() }
                    "completed"
                } catch (error: ResponseStatusException) {
                    assertEquals(HttpStatus.CONFLICT, error.statusCode)
                    "conflict"
                }
            } }
            start.countDown()
            assertEquals(listOf("completed", "conflict"), actions.map { it.get(20, TimeUnit.SECONDS) }.sorted())
        }
        val status = jdbc.queryForObject("SELECT status FROM wiki_request WHERE id = ?", String::class.java, requestNo)
        val count = jdbc.queryForObject("SELECT COUNT(*) FROM wiki_template WHERE title = ?", Long::class.java, title)
        assertEquals(if (status == "APPROVED") 1L else 0L, count)
        assertTrue(status in setOf("APPROVED", "CANCELLED"))
    }

    private fun <T> tx(action: () -> T): T = transaction.execute { action() }!!
}
