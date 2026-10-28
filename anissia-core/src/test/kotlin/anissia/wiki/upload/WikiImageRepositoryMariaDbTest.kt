package anissia.wiki.upload

import anissia.support.FailException
import anissia.wiki.media.ConvertedImage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class WikiImageRepositoryMariaDbTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactions: DataSourceTransactionManager
    private lateinit var transaction: TransactionStatus
    private lateinit var repository: WikiImageRepository
    private var owner = 0L
    private val ids = mutableListOf<String>()

    @BeforeEach
    fun begin() {
        val dataSource = DriverManagerDataSource(System.getenv("ANISSIA_TEST_DB_URL"),
            requireNotNull(System.getenv("ANISSIA_TEST_DB_USER")), requireNotNull(System.getenv("ANISSIA_TEST_DB_PASSWORD")))
        jdbc = JdbcTemplate(dataSource)
        transactions = DataSourceTransactionManager(dataSource)
        transaction = transactions.getTransaction(DefaultTransactionDefinition())
        jdbc.queryForObject("SELECT id FROM wiki_upload_queue WHERE id = 1 FOR UPDATE", Int::class.java)
        assumeTrue(jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE status IN ('WAITING', 'PROCESSING')", Long::class.java) == 0L,
            "Active uploads must finish before running the rollback-only queue tests")
        repository = WikiImageRepository(jdbc)
        val unique = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO account (email, password, name, reg_dt, last_login_dt) VALUES (?, 'unused', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "$unique@wiki.invalid", "wiki-${unique.take(8)}")
        owner = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    @AfterEach
    fun rollback() {
        if (::transaction.isInitialized) transactions.rollback(transaction)
        for (id in ids) assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE id = ?", Long::class.java, id))
    }

    private fun enqueue(limits: WikiUploadProperties = WikiUploadProperties()): WikiImage {
        val id = UUID.randomUUID().toString().replace("-", "")
        ids += id
        return repository.enqueue(id, owner, "queue-test-$id", "", limits)
    }

    @Test
    fun `pending queue enforces both account and global bounds`() {
        enqueue()
        assertThrows<FailException> { enqueue(WikiUploadProperties(maxPendingPerAccount = 1)) }
        assertThrows<FailException> { enqueue(WikiUploadProperties(maxQueueSize = 1)) }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE owner_an = ?", Long::class.java, owner))
    }

    @Test
    fun `one active job and expired restart claim use different tokens`() {
        val first = enqueue()
        enqueue()
        val original = requireNotNull(repository.claim(180))
        assertEquals(first.id, original.id)
        assertNull(repository.claim(180))
        jdbc.update("UPDATE wiki_image SET updated_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 10 MINUTE) WHERE id = ?", original.id)
        val retry = requireNotNull(repository.claim(180))
        assertEquals(first.id, retry.id)
        assertNotEquals(original.claimToken, retry.claimToken)
        var published = false
        assertFalse(repository.complete(original.id, original.claimToken!!, result(), 3) { published = true })
        assertFalse(published)
        assertTrue(repository.complete(retry.id, retry.claimToken!!, result(), 3) { published = true })
        assertTrue(published)
        assertEquals(WikiImageStatus.READY, repository.find(retry.id)!!.status)
        assertEquals(2L, repository.find(retry.id)!!.bytes)
        assertEquals(3L, repository.find(retry.id)!!.thumbnailBytes)
    }

    @Test
    fun `storage publication failure cannot mark a job ready`() {
        enqueue()
        val job = requireNotNull(repository.claim(180))
        assertThrows<IOException> { repository.complete(job.id, job.claimToken!!, result(), 3) { throw IOException("disk full") } }
        assertEquals(WikiImageStatus.PROCESSING, repository.find(job.id)!!.status)
        repository.fail(job.id, "wrong-token", "STORAGE_FAILED")
        assertEquals(WikiImageStatus.PROCESSING, repository.find(job.id)!!.status)
        repository.fail(job.id, job.claimToken!!, "STORAGE_FAILED")
        assertEquals(WikiImageStatus.FAILED, repository.find(job.id)!!.status)
    }

    @Test
    fun `repeatedly interrupted jobs stop after three attempts`() {
        val image = enqueue()
        repeat(3) {
            assertEquals(image.id, repository.claim(180)!!.id)
            jdbc.update("UPDATE wiki_image SET updated_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 10 MINUTE) WHERE id = ?", image.id)
        }
        assertNull(repository.claim(180))
        assertEquals(WikiImageStatus.FAILED, repository.find(image.id)!!.status)
        assertEquals("INTERRUPTED", repository.find(image.id)!!.error)
    }

    @Test
    fun `image timestamps use database epoch instead of JVM timezone`() {
        jdbc.execute("SET time_zone = '+00:00'")
        val image = enqueue()
        val expected = jdbc.queryForObject("SELECT UNIX_TIMESTAMP(created_at) FROM wiki_image WHERE id = ?", java.math.BigDecimal::class.java, image.id)!!
        assertEquals(expected.toLong(), image.createdAt.epochSecond)
        assertEquals(expected.remainder(java.math.BigDecimal.ONE).movePointRight(9).toInt(), image.createdAt.nano)
        val locked = repository.lock(image.id)!!
        assertEquals(image.createdAt, locked.createdAt)
    }

    @Test
    fun `metadata edits preserve image identity and reject stale revision`() {
        val image = ready()
        var audited = false
        val edited = repository.edit(image.id, 1, "새 설명", "https://example.com/source", "편집자") { audited = true }
        assertEquals(2L, edited.revision)
        assertEquals("새 설명", edited.description)
        assertEquals("편집자", edited.updatedBy)
        assertTrue(audited)
        assertThrows<ResponseStatusException> { repository.edit(image.id, 1, "덮어쓰기", "", "편집자") }
        assertEquals("새 설명", repository.find(image.id)!!.description)
    }

    @Test
    fun `current template and document image usage prevents removal`() {
        val image = ready()
        val url = "http://localhost:8001/wiki/files/wiki/${image.id}.avif"
        jdbc.update("INSERT INTO wiki_template (title, deleted, body, revision, updated_at, updated_by) VALUES (?, FALSE, '{}', 1, CURRENT_TIMESTAMP(6), 'fixture')", "usage-${image.id}")
        val templateNo = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbc.update("INSERT INTO wiki_reference (owner_kind, owner_no, target_kind, target_id) VALUES ('template', ?, 'image', ?)", templateNo, url)
        val usages = repository.usage(url, 0, true)
        assertEquals(1L, usages.currentCount)
        assertFalse(usages.canDelete)
        assertEquals("template", usages.content.single().kind)
        assertThrows<FailException> { repository.beginDelete(image.id, 1, "운영자", url) }
        assertEquals(WikiImageStatus.READY, repository.find(image.id)!!.status)
    }

    @Test
    fun `historical body and template snapshots both prevent image removal`() {
        val image = ready()
        val url = "http://localhost:8001/wiki/files/wiki/${image.id}.avif"
        val body = """{"schemaVersion":1,"blocks":[{"w":"img","a":{"src":"$url"}}]}"""
        val insert = """INSERT INTO wiki_revision (kind, target_no, revision, title, body, templates, summary, author_no, updated_by, updated_at)
            VALUES ('template', ?, ?, '이전 템플릿', ?, ?, '', ?, 'fixture', CURRENT_TIMESTAMP(6))"""
        jdbc.update(insert, Long.MAX_VALUE - owner, 1, body, "[]", owner)
        jdbc.update(insert, Long.MAX_VALUE - owner, 2, "{\"schemaVersion\":1,\"blocks\":[]}", "[{\"body\":$body}]", owner)
        val usages = repository.usage(url, 0, true, true)
        assertEquals(0L, usages.currentCount)
        assertEquals(2L, usages.historyCount)
        assertEquals(setOf(1L, 2L), usages.content.map { it.revision }.toSet())
        val publicUsage = repository.usage(url, 0, true)
        assertTrue(publicUsage.content.isEmpty())
        assertEquals(2L, publicUsage.historyCount)
        assertFalse(publicUsage.canDelete)
        assertThrows<FailException> { repository.beginDelete(image.id, 1, "운영자", url) }
    }

    @Test
    fun `anime thumbnail alone prevents deletion and is deduplicated with wiki usage`() {
        val image = ready()
        val url = "http://localhost:8001/wiki/files/wiki/${image.id}.avif"
        val thumbnail = url.removeSuffix(".avif") + ".thumb.avif"
        jdbc.update("""INSERT INTO anime (status, week, time, subject, original_subject, autocorrect, genres,
            start_date, end_date, website, x, note, caption_count, upd_dt, thumbnail)
            VALUES ('ON', '0', '00:00', ?, '', '', '', '', '', '', '', '', 0, CURRENT_TIMESTAMP, ?)""",
            "thumbnail-${image.id}", thumbnail)
        val animeNo = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        assertEquals(1L, repository.usage(url, 0, true).currentCount)
        assertThrows<FailException> { repository.beginDelete(image.id, 1, "운영자", url) }
        jdbc.update("INSERT INTO wiki_reference (owner_kind, owner_no, target_kind, target_id) VALUES ('anime', ?, 'image', ?)", animeNo, url)
        val usages = repository.usage(url, 0, true)
        assertEquals(1L, usages.currentCount)
        assertEquals(animeNo, usages.content.single().targetNo)
        assertFalse(usages.canDelete)
    }

    @Test
    fun `delete and restore persist intent and can resume after file operation interruption`() {
        val image = ready()
        val url = "http://localhost:8001/wiki/files/wiki/${image.id}.avif"
        repository.beginDelete(image.id, 1, "운영자", url)
        assertEquals(WikiImageStatus.DELETING, repository.find(image.id)!!.status)
        assertThrows<IOException> { repository.applyFileOperation(image.id) { throw IOException("interrupted") } }
        repository.fileOperationFailed(image.id)
        assertEquals(WikiImageStatus.DELETING, repository.find(image.id)!!.status)
        repository.applyFileOperation(image.id) { }
        assertEquals(WikiImageStatus.DELETED, repository.find(image.id)!!.status)
        assertTrue(repository.list(0, image.description).first.isEmpty())
        assertEquals(image.id, repository.list(0, image.description, true).first.single().id)
        repository.beginRestore(image.id, 2, "운영자")
        assertEquals(WikiImageStatus.RESTORING, repository.find(image.id)!!.status)
        repository.applyFileOperation(image.id) { }
        assertEquals(WikiImageStatus.READY, repository.find(image.id)!!.status)
        assertEquals(3L, repository.find(image.id)!!.revision)
    }

    private fun ready(): WikiImage {
        val image = enqueue()
        jdbc.update("UPDATE wiki_image SET status = 'READY', width = 1, height = 1, frames = 1, bytes = 2, animated = FALSE WHERE id = ?", image.id)
        return repository.find(image.id)!!
    }

    private fun result() = ConvertedImage(1, 1, 1, 0, false, false, 2)
}
