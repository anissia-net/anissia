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
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.io.TempDir
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class WikiImageRepositoryMariaDbTest {
    @TempDir lateinit var files: Path
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
        repository = WikiImageRepository(jdbc, org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher::class.java))
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
        val templateNo = template()
        jdbc.update("UPDATE wiki_image SET document_kind = 'template', document_no = ? WHERE id = ?", templateNo, image.id)
        val url = "http://localhost:8001/wiki/files/wiki/${image.id}.avif"
        repository.beginDelete(image.id, 1, "운영자", url)
        assertEquals(WikiImageStatus.DELETING, repository.find(image.id)!!.status)
        assertThrows<IOException> { repository.applyFileOperation(image.id) { throw IOException("interrupted") } }
        repository.fileOperationFailed(image.id)
        assertEquals(WikiImageStatus.DELETING, repository.find(image.id)!!.status)
        repository.applyFileOperation(image.id) { }
        assertEquals(WikiImageStatus.DELETED, repository.find(image.id)!!.status)
        assertTrue(repository.list(0, image.description, false, "template", templateNo).first.isEmpty())
        assertEquals(image.id, repository.list(0, image.description, true, "template", templateNo).first.single().id)
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

    @Test
    fun `requested image stays private until approval commits and publication finishes`() {
        val id = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        val templateNo = template()
        repository.enqueue(id, owner, "등록 요청", "https://example.com", WikiUploadProperties(), "REQUEST", documentKind = "template", documentNo = templateNo)
        val job = requireNotNull(repository.claim(180))
        var published = false
        repository.complete(id, job.claimToken!!, result(), 3) { published = true }
        assertFalse(published)
        assertEquals(WikiImageStatus.REVIEW_READY, repository.find(id)!!.status)
        assertTrue(repository.list(0, "등록 요청", false, "template", templateNo).first.isEmpty())
        assertTrue(repository.pendingPublications().isEmpty())
        repository.approveRequest(id)
        assertEquals(WikiImageStatus.APPROVING, repository.find(id)!!.status)
        repository.publishPrepared(id) { _, target -> assertNull(target); published = true }
        assertTrue(published)
        assertEquals(WikiImageStatus.READY, repository.find(id)!!.status)
        assertFalse(repository.publishPrepared(id) { _, _ -> throw AssertionError("duplicate publication") })
    }

    @Test
    fun `cancelled request cannot publish even when its conversion finishes`() {
        val id = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(id, owner, "취소 요청", "https://example.com", WikiUploadProperties(), "REQUEST")
        val job = requireNotNull(repository.claim(180))
        repository.discardRequest(id)
        assertFalse(repository.complete(id, job.claimToken!!, result(), 3) { throw AssertionError("published") })
        assertThrows<FailException> { repository.approveRequest(id) }
        assertEquals("CANCELLED", repository.find(id)!!.error)
    }

    @Test
    fun `replacement keeps current bytes and metadata until successful single publication`() {
        val target = ready()
        val id = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(id, owner, "교체 설명", "https://example.com/new", WikiUploadProperties(), "REPLACEMENT", target.id, 1, "편집자")
        assertTrue(repository.find(target.id)!!.replacementPending)
        assertEquals(target.description, repository.find(target.id)!!.description)
        assertThrows<FailException> { repository.edit(target.id, 1, "경합", "", "다른 사람") }
        assertThrows<FailException> { repository.beginDelete(target.id, 1, "운영자", "http://localhost:8001/wiki/files/wiki/${target.id}.avif") }
        val job = requireNotNull(repository.claim(180))
        repository.complete(id, job.claimToken!!, result().copy(bytes = 9), 4) { throw AssertionError("early publication") }
        assertEquals(WikiImageStatus.REPLACING, repository.find(id)!!.status)
        assertThrows<IOException> { repository.publishPrepared(id) { _, _ -> throw IOException("disk unavailable") } }
        assertEquals(1L, repository.find(target.id)!!.revision)
        repository.publishPrepared(id) { current, original -> assertEquals(target.id, original!!.id); assertEquals(9L, current.bytes) }
        val replaced = repository.find(target.id)!!
        assertEquals("교체 설명", replaced.description)
        assertEquals("https://example.com/new", replaced.sourceUrl)
        assertEquals(9L, replaced.bytes)
        assertEquals(2L, replaced.revision)
        assertFalse(replaced.replacementPending)
        assertEquals(WikiImageStatus.REPLACED, repository.find(id)!!.status)
        assertFalse(repository.publishPrepared(id) { _, _ -> throw AssertionError("duplicate replacement") })
    }

    private fun template(): Long {
        jdbc.update("INSERT INTO wiki_template (title, deleted, body, revision, updated_at, updated_by) VALUES (?, FALSE, '{}', 1, CURRENT_TIMESTAMP(6), 'fixture')", "scope-${UUID.randomUUID()}")
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
    }

    @Test
    fun `document validation rejects malformed missing and deleted owners`() {
        val templateNo = template()
        repository.requireDocument("template", templateNo, true)
        assertThrows<IllegalArgumentException> { repository.requireDocument(null, null) }
        assertThrows<IllegalArgumentException> { repository.requireDocument("other", templateNo) }
        assertThrows<IllegalArgumentException> { repository.requireDocument("template", 0) }
        assertThrows<ResponseStatusException> { repository.requireDocument("template", Long.MAX_VALUE) }
        jdbc.update("UPDATE wiki_template SET deleted = TRUE WHERE template_no = ?", templateNo)
        assertThrows<ResponseStatusException> { repository.requireDocument("template", templateNo, true) }
    }

    @Test
    fun `migrated document copies stay independent and ownerless listing is rejected`() {
        val source = ready()
        val firstNo = template()
        val secondNo = template()
        val firstId = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        val secondId = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.lock(source.id)
        val first = migratedCopy(source, firstId, firstNo)
        val second = migratedCopy(source, secondId, secondNo)
        assertEquals(source.id, first.sourceImageId)
        assertEquals("template", first.documentKind)
        assertEquals(firstNo, first.documentNo)
        assertEquals(source.createdAt, first.createdAt)
        assertThrows<IllegalArgumentException> { repository.list(0, source.description) }
        assertEquals(listOf(first.id), repository.list(0, source.description, false, "template", firstNo).first.map { it.id })
        assertEquals(listOf(second.id), repository.list(0, source.description, false, "template", secondNo).first.map { it.id })
        repository.edit(first.id, first.revision, "첫 문서 전용 설명", "", "fixture")
        assertEquals(source.description, repository.find(source.id)!!.description)
        assertEquals(source.description, repository.find(second.id)!!.description)
        val duplicateId = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        assertThrows<org.springframework.dao.DataIntegrityViolationException> {
            migratedCopy(source, duplicateId, firstNo)
        }
    }

    @Test
    fun `scoped upload stays private until approval and approved intent finishes after document deletion`() {
        val templateNo = template()
        val id = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(id, owner, "문서 첨부 요청", "https://example.com", WikiUploadProperties(), "REQUEST",
            documentKind = "template", documentNo = templateNo)
        val job = requireNotNull(repository.claim(180))
        repository.complete(id, job.claimToken!!, result(), 3) { throw AssertionError("unapproved publication") }
        assertEquals(templateNo, repository.find(id)!!.documentNo)
        assertTrue(repository.list(0, "문서 첨부 요청", false, "template", templateNo).first.isEmpty())
        repository.approveRequest(id)
        jdbc.update("UPDATE wiki_template SET deleted = TRUE WHERE template_no = ?", templateNo)
        assertTrue(repository.publishPrepared(id) { current, target -> assertEquals(templateNo, current.documentNo); assertNull(target) })
        assertEquals(WikiImageStatus.READY, repository.find(id)!!.status)
        jdbc.update("UPDATE wiki_template SET deleted = FALSE WHERE template_no = ?", templateNo)
        assertFalse(repository.publishPrepared(id) { _, _ -> throw AssertionError("duplicate publication") })
        assertEquals(listOf(id), repository.list(0, "문서 첨부 요청", false, "template", templateNo).first.map { it.id })
        assertThrows<IllegalArgumentException> { repository.list(0, "문서 첨부 요청") }
    }

    @Test
    fun `permanent document purge keeps image ownership and files while hiding attachments`() {
        val source = ready()
        val templateNo = template()
        val copyId = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        migratedCopy(source, copyId, templateNo)
        val pending = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(pending, owner, "purge-request", "", WikiUploadProperties(), "REQUEST", documentKind = "template", documentNo = templateNo)
        val job = requireNotNull(repository.claim(180))
        repository.complete(pending, job.claimToken!!, result(), 3) { throw AssertionError("early publication") }
        val approved = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(approved, owner, "purge-approved", "", WikiUploadProperties(), "REQUEST", documentKind = "template", documentNo = templateNo)
        val approvedJob = requireNotNull(repository.claim(180))
        repository.complete(approved, approvedJob.claimToken!!, result(), 3) { throw AssertionError("early approved publication") }
        repository.approveRequest(approved)
        repository.archiveDocument("template", templateNo)
        val archived = repository.find(copyId)!!
        assertEquals("template", archived.documentKind)
        assertEquals(templateNo, archived.documentNo)
        assertFalse(archived.attachmentActive)
        assertEquals(source.id, archived.sourceImageId)
        assertEquals(WikiImageStatus.READY, archived.status)
        assertEquals(WikiImageStatus.FAILED, repository.find(pending)!!.status)
        assertEquals("DOCUMENT_UNAVAILABLE", repository.find(pending)!!.error)
        assertFalse(repository.publishPrepared(pending) { _, _ -> throw AssertionError("ownerless cancelled publication") })
        assertTrue(repository.list(0, source.description, false, "template", templateNo).first.isEmpty())
        val detached = repository.find(approved)!!
        assertEquals(WikiImageStatus.APPROVING, detached.status)
        assertEquals(approvedJob.claimToken, detached.claimToken)
        assertEquals("template", detached.documentKind)
        assertFalse(detached.attachmentActive)
        assertTrue(repository.publishPrepared(approved) { current, target -> assertEquals("template", current.documentKind); assertNull(target) })
        assertTrue(repository.list(0, "purge-approved", false, "template", templateNo).first.isEmpty())
    }

    @Test
    fun `replacement recovers after file write before database acknowledgement and permanent parent purge`() {
        val source = ready()
        val templateNo = template()
        val targetId = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        migratedCopy(source, targetId, templateNo)
        val jobId = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(jobId, owner, "교체 후 설명", "https://example.com/new", WikiUploadProperties(), "REPLACEMENT",
            targetId, 1, "fixture", "template", templateNo)
        val job = requireNotNull(repository.claim(180))
        val storage = WikiImageStorage(WikiUploadProperties(tempRoot = files.resolve("temp").toString(),
            publicRoot = files.resolve("public").toString(), originalRoot = files.resolve("originals").toString()))
        storage.ensureDirectories()
        Files.write(storage.publicFile(targetId), byteArrayOf(1, 2))
        Files.write(storage.thumbnailFile(targetId), byteArrayOf(3))
        storage.receive(jobId, byteArrayOf(4).inputStream())
        Files.write(storage.output(jobId, job.claimToken!!), byteArrayOf(5, 6, 7))
        Files.write(storage.thumbnailOutput(jobId, job.claimToken), byteArrayOf(8))
        repository.complete(jobId, job.claimToken, result().copy(bytes = 3), 1) { throw AssertionError("early replacement") }
        assertThrows<IOException> {
            repository.publishPrepared(jobId) { current, target ->
                storage.replace(current, requireNotNull(target))
                throw IOException("interrupted after file publication before database acknowledgement")
            }
        }
        assertEquals(1L, repository.find(targetId)!!.revision)
        assertEquals(source.description, repository.find(targetId)!!.description)
        assertEquals(listOf<Byte>(5, 6, 7), Files.readAllBytes(storage.publicFile(targetId)).toList())
        repository.archiveDocument("template", templateNo)
        jdbc.update("DELETE FROM wiki_template WHERE template_no = ?", templateNo)
        assertEquals(WikiImageStatus.REPLACING, repository.find(jobId)!!.status)
        assertEquals("template", repository.find(jobId)!!.documentKind)
        assertFalse(repository.find(jobId)!!.attachmentActive)
        assertTrue(repository.publishPrepared(jobId) { current, target -> storage.replace(current, requireNotNull(target)) })
        val recovered = repository.find(targetId)!!
        assertEquals(2L, recovered.revision)
        assertEquals("교체 후 설명", recovered.description)
        assertEquals(3L, recovered.bytes)
        assertEquals("template", recovered.documentKind)
        assertFalse(recovered.attachmentActive)
        assertEquals(WikiImageStatus.REPLACED, repository.find(jobId)!!.status)
        assertEquals(listOf<Byte>(1, 2), Files.readAllBytes(files.resolve("originals/replacements/$targetId/$jobId/image.avif")).toList())
        assertEquals(listOf<Byte>(5, 6, 7), Files.readAllBytes(storage.publicFile(targetId)).toList())
    }

    private fun result() = ConvertedImage(1, 1, 1, 0, false, false, 2)

    @Test
    fun `attachment cancellation preserves original ownership and history then document reuse reactivates it`() {
        val source = ready()
        val templateNo = template()
        val id = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        val image = migratedCopy(source, id, templateNo)
        val url = "http://localhost:8001/wiki/files/wiki/$id.avif"
        val body = """{"schemaVersion":1,"blocks":[{"w":"img","a":{"src":"$url"}}]}"""
        jdbc.update("""INSERT INTO wiki_revision (kind, target_no, revision, title, body, templates, summary, author_no, updated_by, updated_at)
            VALUES ('template', ?, 1, '첨부 이력', ?, '[]', '', ?, 'fixture', CURRENT_TIMESTAMP(6))""", templateNo, body, owner)
        var audited = false

        val detached = repository.detach(id, 1, "편집자", url) { audited = true }

        assertTrue(audited)
        assertFalse(detached.attachmentActive)
        assertEquals(2L, detached.revision)
        assertEquals(WikiImageStatus.READY, detached.status)
        assertEquals("template", detached.documentKind)
        assertEquals(templateNo, detached.documentNo)
        assertEquals(source.id, detached.sourceImageId)
        assertEquals(image.bytes, detached.bytes)
        assertEquals(source, repository.find(source.id))
        assertEquals(0L to 1L, repository.usageCounts(url))
        assertTrue(repository.list(0, source.description, false, "template", templateNo).first.isEmpty())
        assertThrows<ResponseStatusException> { repository.detach(id, 1, "다른 편집자", url) }
        assertEquals(detached, repository.detach(id, 2, "편집자", url))

        repository.activateAttachment(id, "template", templateNo, "되돌린 편집자")
        val restored = repository.find(id)!!
        assertTrue(restored.attachmentActive)
        assertEquals(3L, restored.revision)
        assertEquals(listOf(id), repository.list(0, source.description, false, "template", templateNo).first.map { it.id })
        assertEquals(0L to 1L, repository.usageCounts(url))
    }

    @Test
    fun `current use replacement and stale revision prevent attachment cancellation`() {
        val source = ready()
        val templateNo = template()
        val id = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        migratedCopy(source, id, templateNo)
        val url = "http://localhost:8001/wiki/files/wiki/$id.avif"
        assertThrows<ResponseStatusException> { repository.detach(id, 0, "편집자", url) }
        jdbc.update("INSERT INTO wiki_reference (owner_kind, owner_no, target_kind, target_id) VALUES ('template', ?, 'image', ?)", templateNo, url)
        assertThrows<FailException> { repository.detach(id, 1, "편집자", url) }
        jdbc.update("DELETE FROM wiki_reference WHERE owner_kind = 'template' AND owner_no = ?", templateNo)
        jdbc.update("""INSERT INTO anime (status, week, time, subject, original_subject, autocorrect, genres,
            start_date, end_date, website, x, note, caption_count, upd_dt, thumbnail)
            VALUES ('ON', '0', '00:00', '첨부 취소 썸네일', '', '', '', '', '', '', '', '', 0, CURRENT_TIMESTAMP, ?)""",
            url.removeSuffix(".avif") + ".thumb.avif")
        val animeNo = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        assertThrows<FailException> { repository.detach(id, 1, "편집자", url) }
        jdbc.update("UPDATE anime SET thumbnail = NULL WHERE anime_no = ?", animeNo)
        val job = UUID.randomUUID().toString().replace("-", "").also(ids::add)
        repository.enqueue(job, owner, "교체", "", WikiUploadProperties(), "REPLACEMENT", id, 1, "fixture", "template", templateNo)
        assertThrows<FailException> { repository.detach(id, 1, "편집자", url) }
        assertTrue(repository.find(id)!!.attachmentActive)
        assertEquals(1L, repository.find(id)!!.revision)
        assertThrows<FailException> { repository.detach(source.id, 1, "편집자", "http://localhost:8001/wiki/files/wiki/${source.id}.avif") }
    }

    private fun migratedCopy(source: WikiImage, id: String, templateNo: Long): WikiImage {
        jdbc.update("""INSERT INTO wiki_image (id, owner_an, status, description, source_url, width, height, frames, bytes, animated,
            thumbnail_bytes, revision, updated_by, submission_kind, document_kind, document_no, source_image_id, created_at, updated_at)
            SELECT ?, owner_an, 'READY', description, source_url, width, height, frames, bytes, animated,
                thumbnail_bytes, revision, updated_by, 'DIRECT', 'template', ?, id, created_at, updated_at
            FROM wiki_image WHERE id = ?""", id, templateNo, source.id)
        return repository.find(id)!!
    }
}
