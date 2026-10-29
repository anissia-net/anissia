package anissia.wiki

import anissia.account.domain.Account
import anissia.account.repository.AccountRepository
import anissia.anime.domain.Anime
import anissia.anime.repository.AnimeRepository
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageService
import anissia.wiki.upload.WikiImageDto
import anissia.wiki.upload.WikiImageStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class WikiConcurrencyMariaDbTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var entityManager: EntityManager
    private lateinit var transaction: TransactionTemplate
    private lateinit var jdbc: JdbcTemplate
    private lateinit var accounts: AccountRepository
    private lateinit var anime: AnimeRepository
    private lateinit var documents: AnimeWikiRepository
    private lateinit var templates: WikiTemplateRepository
    private lateinit var service: WikiContentService
    private lateinit var images: WikiImageService
    private var accountNo = 0L
    private var animeNo = 0L
    private val templateIds = mutableListOf<Long>()

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
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(persistence)
        val repositories = JpaRepositoryFactory(entityManager)
        accounts = repositories.getRepository(AccountRepository::class.java)
        anime = repositories.getRepository(AnimeRepository::class.java)
        documents = repositories.getRepository(AnimeWikiRepository::class.java)
        templates = repositories.getRepository(WikiTemplateRepository::class.java)
        val manager = JpaTransactionManager(persistence).apply { dataSource = source; afterPropertiesSet() }
        transaction = TransactionTemplate(manager).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            timeout = 15
        }
        images = mock(WikiImageService::class.java)
        service = WikiContentService(WikiAccessService(accounts), anime, documents, templates,
            repositories.getRepository(WikiRevisionRepository::class.java),
            repositories.getRepository(WikiReferenceRepository::class.java), images, mock(anissia.search.ContentSearchRepository::class.java), mock(org.springframework.context.ApplicationEventPublisher::class.java))
        val unique = UUID.randomUUID().toString()
        tx {
            accountNo = accounts.saveAndFlush(Account(email = "$unique@wiki.invalid", name = "wiki-${unique.take(8)}",
                password = "not-a-login-password", regDt = OffsetDateTime.now().minusDays(30))).an
            animeNo = anime.saveAndFlush(Anime(subject = "wiki-concurrency-$unique")).animeNo
        }
    }

    @AfterEach
    fun removeOnlyCreatedFixtures() {
        try {
            if (::transaction.isInitialized && animeNo > 0) tx {
                service.deleteForAnime(animeNo)
                anime.deleteById(animeNo)
                for (id in templateIds) {
                    jdbc.update("DELETE FROM wiki_reference WHERE owner_kind = 'template' AND owner_no = ?", id)
                    jdbc.update("DELETE FROM wiki_revision WHERE kind = 'template' AND target_no = ?", id)
                    templates.deleteById(id)
                }
                accounts.deleteById(accountNo)
            }
            if (::jdbc.isInitialized && animeNo > 0) {
                assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM anime WHERE anime_no = ?", Long::class.java, animeNo))
                assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE an = ?", Long::class.java, accountNo))
            }
        } finally {
            if (::factory.isInitialized) factory.destroy()
        }
    }

    @Test
    fun `two first edits with prior reads commit only one first revision`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val edits = listOf("first", "second").map { text -> executor.submit<String> {
                try {
                    tx {
                        primeSnapshot()
                        ready.countDown()
                        await(start)
                        service.editAnime(animeNo, WikiEditRequest(0, textBody(text)), Actor(an = accountNo))
                    }
                    "saved"
                } catch (error: ResponseStatusException) {
                    assertEquals(HttpStatus.CONFLICT, error.statusCode)
                    "conflict"
                }
            } }
            try {
                await(ready)
            } finally {
                start.countDown()
            }
            assertEquals(listOf("conflict", "saved"), edits.map { it.get(15, TimeUnit.SECONDS) }.sorted())
        }
        assertEquals(1L, jdbc.queryForObject("SELECT revision FROM anime_wiki WHERE anime_no = ?", Long::class.java, animeNo))
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_revision WHERE kind = 'anime' AND target_no = ?", Long::class.java, animeNo))
    }

    @Test
    fun `an edit waiting on the parent sees the newly committed revision`() {
        tx { service.editAnime(animeNo, WikiEditRequest(0, textBody("original")), Actor(an = accountNo)) }
        val edited = CountDownLatch(1)
        val snapshot = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val writer = executor.submit {
                tx {
                    service.editAnime(animeNo, WikiEditRequest(1, textBody("committed")), Actor(an = accountNo))
                    edited.countDown()
                    await(release)
                }
            }
            await(edited)
            val stale = executor.submit<String> {
                try {
                    tx {
                        primeSnapshot()
                        snapshot.countDown()
                        service.editAnime(animeNo, WikiEditRequest(1, textBody("stale")), Actor(an = accountNo))
                    }
                    "saved"
                } catch (error: ResponseStatusException) {
                    assertEquals(HttpStatus.CONFLICT, error.statusCode)
                    "conflict"
                }
            }
            try { await(snapshot) } finally { release.countDown() }
            writer.get(15, TimeUnit.SECONDS)
            assertEquals("conflict", stale.get(15, TimeUnit.SECONDS))
        }
        assertEquals(2L, jdbc.queryForObject("SELECT revision FROM anime_wiki WHERE anime_no = ?", Long::class.java, animeNo))
    }

    @Test
    fun `anime deletion removes references and revisions committed while it waited`() {
        val templateNo = createTemplate()
        tx { service.editAnime(animeNo, WikiEditRequest(0, textBody("original")), Actor(an = accountNo)) }
        val edited = CountDownLatch(1)
        val snapshot = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val writer = executor.submit {
                tx {
                    service.editAnime(animeNo, WikiEditRequest(1, include(templateNo)), Actor(an = accountNo))
                    edited.countDown()
                    await(release)
                }
            }
            await(edited)
            val deletion = executor.submit {
                tx {
                    primeSnapshot()
                    snapshot.countDown()
                    service.deleteForAnime(animeNo)
                    anime.deleteById(animeNo)
                }
            }
            try { await(snapshot) } finally { release.countDown() }
            writer.get(15, TimeUnit.SECONDS)
            deletion.get(15, TimeUnit.SECONDS)
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM anime_wiki WHERE anime_no = ?", Long::class.java, animeNo))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_revision WHERE kind = 'anime' AND target_no = ?", Long::class.java, animeNo))
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_reference WHERE owner_kind = 'anime' AND owner_no = ?", Long::class.java, animeNo))
    }

    @Test
    fun `template delete sees newly committed usage after waiting for its lock`() {
        val templateNo = createTemplate()
        tx { jdbc.update("INSERT INTO account_role (an, role) VALUES (?, 'TRANSLATOR')", accountNo) }
        val edited = CountDownLatch(1)
        val snapshot = CountDownLatch(1)
        val release = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val writer = executor.submit {
                tx {
                    service.editAnime(animeNo, WikiEditRequest(0, include(templateNo)), Actor(an = accountNo))
                    edited.countDown()
                    await(release)
                }
            }
            await(edited)
            val deletion = executor.submit<String> {
                try {
                    tx {
                        primeSnapshot()
                        snapshot.countDown()
                        service.deleteTemplate(templateNo, 1, Actor(an = accountNo))
                    }
                    "deleted"
                } catch (_: FailException) { "in-use" }
            }
            try { await(snapshot) } finally { release.countDown() }
            writer.get(15, TimeUnit.SECONDS)
            assertEquals("in-use", deletion.get(15, TimeUnit.SECONDS))
        }
        assertFalse(jdbc.queryForObject("SELECT deleted FROM wiki_template WHERE template_no = ?", Boolean::class.java, templateNo)!!)
    }

    @Test
    fun `recent usage and history queries preserve live titles and historical template snapshots`() {
        val templateNo = createTemplate()
        tx { service.editAnime(animeNo, WikiEditRequest(0, include(templateNo)), Actor(an = accountNo)) }
        tx {
            val template = service.templateDocument(templateNo)
            service.editTemplate(templateNo, WikiEditRequest(1, textBody("updated shared text"), title = template.title), Actor(an = accountNo))
            val source = anime.findById(animeNo).orElseThrow()
            source.subject += " renamed"
            anime.saveAndFlush(source)
        }
        tx {
            val usage = service.templateUsage(templateNo, 0, Actor())
            assertEquals(1L, usage.totalElements)
            assertEquals(animeNo, usage.content.single().targetNo)
            assertTrue(usage.content.single().title.endsWith(" renamed"))
            val recent = service.recent(0, Actor())
            assertTrue(recent.content.any { it.kind == "anime" && it.targetNo == animeNo && it.title.endsWith(" renamed") })
            assertEquals(2L, service.animeDocument(animeNo).templates.single().revision)
            val history = service.revision("anime", animeNo, 1)
            assertEquals(1L, history.templates.single().revision)
            assertEquals(textBody("shared"), history.templates.single().body)
        }
    }

    @Test
    fun `automatic thumbnail persists and a previously loaded ordinary edit preserves it`() {
        val id = "a".repeat(32)
        val url = "https://file.example/wiki/$id.avif"
        val thumbnail = "https://file.example/wiki/$id.thumb.avif"
        `when`(images.thumbnailCandidates(listOf(url), true)).thenReturn(listOf(
            WikiImageDto(id, WikiImageStatus.READY, "fixture", "", Instant.EPOCH, url = url, thumbnailUrl = thumbnail),
        ))
        val loaded = CountDownLatch(1)
        val selected = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val ordinaryEdit = executor.submit {
                tx {
                    val stale = anime.findById(animeNo).orElseThrow()
                    loaded.countDown()
                    await(selected)
                    stale.note = "ordinary edit"
                    anime.saveAndFlush(stale)
                }
            }
            await(loaded)
            tx {
                val body = WikiBody(blocks = Json.readTree("""[{"w":"img","a":{"src":"$url"}}]"""))
                service.editAnime(animeNo, WikiEditRequest(0, body), Actor(an = accountNo))
            }
            assertEquals(thumbnail, jdbc.queryForObject("SELECT thumbnail FROM anime WHERE anime_no = ?", String::class.java, animeNo))
            selected.countDown()
            ordinaryEdit.get(10, TimeUnit.SECONDS)
            assertEquals(thumbnail, jdbc.queryForObject("SELECT thumbnail FROM anime WHERE anime_no = ?", String::class.java, animeNo))
            assertEquals("ordinary edit", jdbc.queryForObject("SELECT note FROM anime WHERE anime_no = ?", String::class.java, animeNo))
        } finally {
            selected.countDown()
            executor.shutdownNow()
        }
    }

    private fun primeSnapshot() {
        entityManager.createNativeQuery("SELECT COUNT(*) FROM wiki_revision WHERE kind = 'anime' AND target_no = :id", Long::class.java)
            .setParameter("id", animeNo).singleResult
    }

    private fun createTemplate(): Long = tx {
        service.createTemplate(WikiEditRequest(0, textBody("shared"), title = "wiki-concurrency-${UUID.randomUUID()}"), Actor(an = accountNo))
            .templateNo!!.also(templateIds::add)
    }

    private fun textBody(text: String) = WikiBody(blocks = Json.readTree("[{\"w\":\"p\",\"ch\":[${Json.write(text)}]}]"))
    private fun include(id: Long) = WikiBody(blocks = Json.readTree("[{\"w\":\"exTemplateInclude\",\"a\":{\"exTemplateNo\":\"$id\"},\"ch\":[]}]"))
    private fun await(latch: CountDownLatch) = assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent transaction did not reach its checkpoint")
    private fun <T : Any> tx(block: () -> T): T = requireNotNull(transaction.execute { block() })
}
