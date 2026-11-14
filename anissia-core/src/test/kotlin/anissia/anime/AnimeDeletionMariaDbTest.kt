package anissia.anime

import anissia.account.domain.Account
import anissia.account.repository.AccountRepository
import anissia.activepanel.repository.ActivePanelRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.agenda.repository.AgendaRepository
import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeCaption
import anissia.anime.repository.*
import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.anime.service.*
import anissia.counter.DerivedCounters
import anissia.notification.repository.AnimeNotificationRepository
import anissia.search.ContentSearchRepository
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.Json
import anissia.translator.service.TranslatorApplyService
import anissia.wiki.*
import anissia.wiki.upload.WikiImageService
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.Mockito.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class AnimeDeletionMariaDbTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var entityManager: EntityManager
    private lateinit var tx: TransactionTemplate
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repositories: JpaRepositoryFactory

    @BeforeEach
    fun openDatabase() {
        val source = DriverManagerDataSource(System.getenv("ANISSIA_TEST_DB_URL"),
            requireNotNull(System.getenv("ANISSIA_TEST_DB_USER")), requireNotNull(System.getenv("ANISSIA_TEST_DB_PASSWORD")))
        jdbc = JdbcTemplate(source)
        factory = LocalContainerEntityManagerFactoryBean().apply {
            dataSource = source
            setPackagesToScan("anissia")
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "none",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
                "hibernate.timezone.default_storage" to "NORMALIZE"))
            afterPropertiesSet()
        }
        val persistence = requireNotNull(factory.`object`)
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(persistence)
        repositories = JpaRepositoryFactory(entityManager)
        tx = TransactionTemplate(JpaTransactionManager(persistence).apply { dataSource = source; afterPropertiesSet() }).apply {
            isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
            timeout = 30
        }
    }

    @AfterEach
    fun closeDatabase() {
        if (::factory.isInitialized) factory.destroy()
    }

    @Test
    fun `hidden work keeps its complete graph and recovers the original ID before permanent deletion`() {
        tx.execute { status ->
            status.setRollbackOnly()
            val anime = repositories.getRepository(AnimeRepository::class.java)
            val accounts = repositories.getRepository(AccountRepository::class.java)
            val captions = repositories.getRepository(AnimeCaptionRepository::class.java)
            val agendas = repositories.getRepository(AgendaRepository::class.java)
            val documents = repositories.getRepository(AnimeWikiRepository::class.java)
            val revisions = repositories.getRepository(WikiRevisionRepository::class.java)
            val references = repositories.getRepository(WikiReferenceRepository::class.java)
            val unique = UUID.randomUUID().toString()
            val account = accounts.saveAndFlush(Account(email = "$unique@deletion.invalid", name = "del-${unique.take(8)}", password = "unused"))
            val work = anime.saveAndFlush(Anime(subject = "deletion-$unique", week = "2", thumbnail = "https://file.invalid/retained.avif"))
            val id = work.animeNo
            captions.saveAndFlush(AnimeCaption(anime = work, an = account.an, website = "https://caption.invalid/retained"))
            jdbc.update("INSERT INTO anime_favorite (an, anime_no, notify) VALUES (?, ?, true)", account.an, id)
            val body = Json.write(WikiBody.empty())
            documents.saveAndFlush(AnimeWiki(id).apply { this.body = body; revision = 1; updatedBy = account.name })
            revisions.saveAndFlush(WikiRevision(kind = "anime", targetNo = id, revision = 1, title = work.subject,
                body = body, authorNo = account.an, updatedBy = account.name))
            references.saveAndFlush(WikiReference(ownerKind = "anime", ownerNo = id, targetKind = "image", targetId = "retained-$unique"))
            entityManager.clear()
            val events = mock(ApplicationEventPublisher::class.java)
            val grants = mock(TranslatorApplyService::class.java)
            `when`(grants.isGrantedBefore90Days(account.an)).thenReturn(true)
            val service = AnimeService(anime, captions, mock(AnimeGenreRepository::class.java), mock(AnimeQueryRepository::class.java),
                mock(DerivedCounters::class.java), mock(AnimeSearchRepository::class.java), mock(AnimeRankService::class.java),
                mock(ActivePanelLogService::class.java), mock(ActivePanelRepository::class.java), agendas, accounts, grants, events)
            val actor = Actor(an = account.an, name = account.name, roles = listOf("ROOT"))

            assertEquals(ApiResponse.OK, service.delete(id, actor).code)
            entityManager.flush()
            entityManager.clear()
            val agenda = agendas.findAllByCodeAndStatusOrderByAgendaNoDesc("ANIME-DEL", "wait", PageRequest.of(0, 1)).content.single()
            assertEquals(1L, count("anime", "anime_no", id))
            assertEquals(1L, count("anime_caption", "anime_no", id))
            assertEquals(1L, count("anime_favorite", "anime_no", id))
            assertEquals(1L, count("anime_wiki", "anime_no", id))
            assertEquals(1L, count("wiki_revision", "target_no", id, " AND kind='anime'"))
            assertEquals(1L, count("wiki_reference", "owner_no", id, " AND owner_kind='anime'"))
            assertNull(anime.findWithCaptionsByAnimeNo(id))
            assertFalse(anime.existsByAnimeNoAndDeletedAtIsNull(id))
            assertTrue(anime.findAllByStatusNotAndWeek(anissia.anime.domain.AnimeStatus.END, "2").none { it.animeNo == id })
            assertTrue(captions.findAllWithAccountByAnimeNo(id).isEmpty())
            assertTrue(repositories.getRepository(AnimeNotificationRepository::class.java).findAllWithAnimeByAn(account.an).isEmpty())

            assertEquals(id, service.recover(agenda.agendaNo, actor).data)
            entityManager.flush()
            entityManager.clear()
            assertNotNull(anime.findWithCaptionsByAnimeNo(id))
            assertEquals(1, captions.findAllWithAccountByAnimeNo(id).size)
            assertEquals(1, repositories.getRepository(AnimeNotificationRepository::class.java).findAllWithAnimeByAn(account.an).size)
            assertEquals(body, documents.findById(id).get().body)

            assertEquals(ApiResponse.OK, service.delete(id, actor).code)
            entityManager.flush()
            val cutoff = OffsetDateTime.now().minusDays(30)
            anime.lockByAnimeNo(id)!!.deletedAt = cutoff
            entityManager.flush()
            entityManager.clear()
            val wiki = WikiContentService(mock(WikiAccessService::class.java), anime, documents,
                repositories.getRepository(WikiTemplateRepository::class.java), revisions, references,
                mock(WikiImageService::class.java), mock(ContentSearchRepository::class.java), events)
            val deletion = AnimeDeletionService(anime, captions, repositories.getRepository(AnimeHitRepository::class.java),
                repositories.getRepository(AnimeHitHourRepository::class.java), agendas, wiki, events)
            assertTrue(deletion.purge(id, cutoff.plusSeconds(1)))
            entityManager.flush()
            entityManager.clear()
            for (table in listOf("anime", "anime_caption", "anime_favorite", "anime_wiki")) assertEquals(0L, count(table, "anime_no", id), table)
            assertEquals(0L, count("wiki_revision", "target_no", id, " AND kind='anime'"))
            assertEquals(0L, count("wiki_reference", "owner_no", id, " AND owner_kind='anime'"))
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM agenda WHERE code='ANIME-DEL' AND status='purged' AND data2=?", Long::class.java, "retained:$id"))
        }
    }

    private fun count(table: String, column: String, id: Long, extra: String = ""): Long =
        requireNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE $column = ?$extra", Long::class.java, id))
}
