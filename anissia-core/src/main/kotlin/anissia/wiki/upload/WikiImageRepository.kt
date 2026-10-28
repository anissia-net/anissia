package anissia.wiki.upload

import anissia.support.FailException
import anissia.wiki.media.ConvertedImage
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Repository
class WikiImageRepository(private val jdbc: JdbcTemplate) {
    private fun lockQueue() {
        check(jdbc.queryForObject("SELECT id FROM wiki_upload_queue WHERE id = 1 FOR UPDATE", Int::class.java) == 1)
    }

    @Transactional
    fun enqueue(id: String, ownerAn: Long, description: String, sourceUrl: String, limits: WikiUploadProperties): WikiImage {
        lockQueue()
        val active = "status IN ('WAITING', 'PROCESSING')"
        if (jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE $active", Long::class.java)!! >= limits.maxQueueSize) {
            throw FailException("이미지 처리 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.")
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE owner_an = ? AND $active", Long::class.java, ownerAn)!! >= limits.maxPendingPerAccount) {
            throw FailException("처리 중인 이미지가 있습니다. 완료 후 다시 업로드해 주세요.")
        }
        jdbc.update(
            """INSERT INTO wiki_image (id, owner_an, status, description, source_url, created_at, updated_at)
                VALUES (?, ?, 'WAITING', ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
            id, ownerAn, description, sourceUrl,
        )
        return requireNotNull(find(id))
    }

    fun find(id: String): WikiImage? = jdbc.query("SELECT $IMAGE_COLUMNS FROM wiki_image WHERE id = ?", ::map, id).firstOrNull()

    @Transactional(propagation = Propagation.MANDATORY)
    fun lock(id: String): WikiImage? = jdbc.query("SELECT $IMAGE_COLUMNS FROM wiki_image WHERE id = ? FOR UPDATE", ::map, id).firstOrNull()

    fun list(page: Int, query: String, deleted: Boolean = false): Pair<List<WikiImage>, Long> {
        val pattern = "%${query.replace("!", "!!").replace("%", "!%").replace("_", "!_")}%"
        val states = if (deleted) "status IN ('DELETING', 'DELETED', 'RESTORING')" else "status = 'READY'"
        val where = "$states AND description LIKE ? ESCAPE '!'"
        val content = jdbc.query("SELECT $IMAGE_COLUMNS FROM wiki_image WHERE $where ORDER BY created_at DESC, id DESC LIMIT 24 OFFSET ?", ::map, pattern, page.toLong() * 24)
        val count = jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE $where", Long::class.java, pattern)!!
        return content to count
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun edit(id: String, baseRevision: Long, description: String, sourceUrl: String, author: String, audit: () -> Unit = {}): WikiImage {
        val current = lock(id) ?: missing()
        checkRevision(baseRevision, current.revision)
        if (current.status != WikiImageStatus.READY) throw FailException("공개된 이미지만 수정할 수 있습니다.")
        if (current.description == description && current.sourceUrl == sourceUrl) return current
        jdbc.update("UPDATE wiki_image SET description = ?, source_url = ?, revision = revision + 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
            description, sourceUrl, author, id)
        audit()
        return requireNotNull(find(id))
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun beginDelete(id: String, baseRevision: Long, author: String, url: String, audit: () -> Unit = {}): WikiImage {
        val current = lock(id) ?: missing()
        checkRevision(baseRevision, current.revision)
        if (current.status == WikiImageStatus.DELETED || current.status == WikiImageStatus.DELETING) return current
        if (current.status != WikiImageStatus.READY) throw FailException("공개된 이미지만 삭제할 수 있습니다.")
        val (currentCount, historyCount) = usageCounts(url)
        if (currentCount > 0 || historyCount > 0) {
            throw FailException("현재 문서·템플릿 ${currentCount}곳 또는 보존된 이력 ${historyCount}판에서 사용하는 이미지는 삭제할 수 없습니다.")
        }
        beginFileOperation(id, "DELETING", author)
        audit()
        return requireNotNull(find(id))
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun beginRestore(id: String, baseRevision: Long, author: String, audit: () -> Unit = {}): WikiImage {
        val current = lock(id) ?: missing()
        checkRevision(baseRevision, current.revision)
        if (current.status == WikiImageStatus.RESTORING || current.status == WikiImageStatus.READY) return current
        if (current.status != WikiImageStatus.DELETED) throw FailException("삭제 처리가 완료된 이미지만 복구할 수 있습니다.")
        beginFileOperation(id, "RESTORING", author)
        audit()
        return requireNotNull(find(id))
    }

    private fun beginFileOperation(id: String, state: String, author: String) {
        jdbc.update("""UPDATE wiki_image SET status = ?, revision = revision + 1, updated_by = ?, error = NULL,
            file_operation_next_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?""", state, author, id)
    }

    fun dueFileOperations(): List<String> = jdbc.query(
        """SELECT id FROM wiki_image WHERE status IN ('DELETING', 'RESTORING')
            AND (file_operation_next_at IS NULL OR file_operation_next_at <= CURRENT_TIMESTAMP(6))
            ORDER BY file_operation_next_at, id LIMIT 20""", { rs, _ -> rs.getString(1) },
    )

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun applyFileOperation(id: String, move: (WikiImage) -> Unit) {
        val current = lock(id) ?: return
        if (current.status !in setOf(WikiImageStatus.DELETING, WikiImageStatus.RESTORING)) return
        move(current)
        jdbc.update("UPDATE wiki_image SET status = ?, error = NULL, file_operation_next_at = NULL, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
            if (current.status == WikiImageStatus.DELETING) "DELETED" else "READY", id)
    }

    fun fileOperationFailed(id: String) {
        jdbc.update("""UPDATE wiki_image SET error = 'FILE_OPERATION_FAILED',
            file_operation_next_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 MINUTE)
            WHERE id = ? AND status IN ('DELETING', 'RESTORING')""", id)
    }

    fun usageCounts(url: String): Pair<Long, Long> {
        val current = jdbc.queryForObject("SELECT COUNT(*) FROM ($CURRENT_USAGE) u", Long::class.java, url, url, url, thumbnailUrl(url))!!
        val history = jdbc.queryForObject("SELECT COUNT(*) FROM ($HISTORY_USAGE) u", Long::class.java, escaped(url), escaped(url))!!
        return current to history
    }

    fun usage(url: String, page: Int, ready: Boolean, includeDeleted: Boolean = false): WikiImageUsagePage {
        val (current, history) = usageCounts(url)
        val visibleHistory = HISTORY_USAGE + if (includeDeleted) "" else """ AND (
            (r.kind = 'anime' AND a.anime_no IS NOT NULL) OR
            (r.kind = 'template' AND EXISTS (SELECT 1 FROM wiki_template t WHERE t.template_no = r.target_no AND t.deleted = FALSE)))"""
        val visibleHistoryCount = if (includeDeleted) history else
            jdbc.queryForObject("SELECT COUNT(*) FROM ($visibleHistory) u", Long::class.java, escaped(url), escaped(url))!!
        val records = jdbc.query("""SELECT * FROM ($CURRENT_USAGE UNION ALL $visibleHistory) u
            ORDER BY scope, kind, target_no, revision DESC LIMIT 30 OFFSET ?""", { rs, _ ->
            val kind = rs.getString("kind")
            val no = rs.getLong("target_no")
            WikiImageUsage(kind, no, rs.getString("title"),
                if (kind == "anime") "/anime/$no" else "/wiki/templates?templateNo=$no",
                rs.getString("scope"), (rs.getObject("revision") as? Number)?.toLong())
        }, url, url, url, thumbnailUrl(url), escaped(url), escaped(url), page.toLong() * 30)
        return WikiImageUsagePage(records, current + visibleHistoryCount, current, history, page, canDelete = ready && current + history == 0L)
    }

    private fun escaped(value: String) = value.replace("!", "!!").replace("%", "!%").replace("_", "!_")
    private fun thumbnailUrl(url: String) = url.removeSuffix(".avif") + ".thumb.avif"

    private fun checkRevision(base: Long, current: Long) {
        if (base < 0 || base != current) throw ResponseStatusException(HttpStatus.CONFLICT, "이미지 정보가 변경되었습니다. 최신 정보를 불러온 뒤 다시 시도해 주세요.")
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 이미지입니다.")

    @Transactional
    fun claim(leaseSeconds: Long): WikiImage? {
        lockQueue()
        jdbc.update(
            """UPDATE wiki_image SET status = IF(attempts >= 3, 'FAILED', 'WAITING'), claim_token = NULL,
                error = IF(attempts >= 3, 'INTERRUPTED', NULL), updated_at = CURRENT_TIMESTAMP(6)
                WHERE status = 'PROCESSING' AND updated_at < TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6))""",
            -leaseSeconds,
        )
        jdbc.update(
            """UPDATE wiki_image SET status = 'FAILED', error = 'EXPIRED', updated_at = CURRENT_TIMESTAMP(6)
                WHERE status = 'WAITING' AND created_at < DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)""",
        )
        if (jdbc.queryForObject("SELECT COUNT(*) FROM wiki_image WHERE status = 'PROCESSING'", Long::class.java)!! > 0) return null
        val id = jdbc.query("SELECT id FROM wiki_image WHERE status = 'WAITING' ORDER BY created_at, id LIMIT 1", { rs, _ -> rs.getString(1) }).firstOrNull() ?: return null
        val token = UUID.randomUUID().toString().replace("-", "")
        jdbc.update("UPDATE wiki_image SET status = 'PROCESSING', claim_token = ?, attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?", token, id)
        return find(id)
    }

    @Transactional
    fun complete(id: String, token: String, image: ConvertedImage, thumbnailBytes: Long, publish: () -> Unit): Boolean {
        val current = lock(id) ?: return false
        if (current.status != WikiImageStatus.PROCESSING || current.claimToken != token) return false
        require(thumbnailBytes > 0)
        publish()
        jdbc.update(
            """UPDATE wiki_image SET status = 'READY', claim_token = NULL, width = ?, height = ?, frames = ?,
                bytes = ?, animated = ?, thumbnail_bytes = ?, error = NULL, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?""",
            image.width, image.height, image.frames, image.bytes, image.animated, thumbnailBytes, id,
        )
        return true
    }

    fun fail(id: String, token: String, reason: String) {
        jdbc.update("UPDATE wiki_image SET status = 'FAILED', claim_token = NULL, error = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND status = 'PROCESSING' AND claim_token = ?", reason, id, token)
    }

    private fun map(rs: ResultSet, row: Int): WikiImage = WikiImage(
        id = rs.getString("id"), ownerAn = (rs.getObject("owner_an") as? Number)?.toLong(),
        status = WikiImageStatus.valueOf(rs.getString("status")), description = rs.getString("description"),
        sourceUrl = rs.getString("source_url"), createdAt = epoch(rs, "created_epoch"),
        updatedAt = epoch(rs, "updated_epoch"), attempts = rs.getInt("attempts"),
        claimToken = rs.getString("claim_token"), width = (rs.getObject("width") as? Number)?.toInt(),
        height = (rs.getObject("height") as? Number)?.toInt(), frames = (rs.getObject("frames") as? Number)?.toInt(),
        bytes = (rs.getObject("bytes") as? Number)?.toLong(),
        animated = rs.getObject("animated")?.let { rs.getBoolean("animated") }, error = rs.getString("error"),
        revision = rs.getLong("revision"), updatedBy = rs.getString("updated_by"),
        thumbnailBytes = (rs.getObject("thumbnail_bytes") as? Number)?.toLong(),
    )

    private fun epoch(rs: ResultSet, field: String): Instant = rs.getBigDecimal(field).let {
        Instant.ofEpochSecond(it.toLong(), it.remainder(BigDecimal.ONE).movePointRight(9).toLong())
    }

    companion object {
        private const val IMAGE_COLUMNS = "*, UNIX_TIMESTAMP(created_at) AS created_epoch, UNIX_TIMESTAMP(updated_at) AS updated_epoch"
        private const val CURRENT_USAGE = """
            SELECT _utf8mb4'anime' COLLATE utf8mb4_bin AS kind, a.anime_no AS target_no,
                CONVERT(a.subject USING utf8mb4) COLLATE utf8mb4_bin AS title,
                _utf8mb4'current' COLLATE utf8mb4_bin AS scope, NULL AS revision
            FROM wiki_reference r JOIN anime a ON r.owner_kind = 'anime' AND r.owner_no = a.anime_no
            WHERE r.target_kind = 'image' AND r.target_id = ?
            UNION
            SELECT _utf8mb4'template' COLLATE utf8mb4_bin, t.template_no,
                CONVERT(t.title USING utf8mb4) COLLATE utf8mb4_bin, _utf8mb4'current' COLLATE utf8mb4_bin, NULL
            FROM wiki_reference r JOIN wiki_template t ON r.owner_kind = 'template' AND r.owner_no = t.template_no AND t.deleted = FALSE
            WHERE r.target_kind = 'image' AND r.target_id = ?
            UNION
            SELECT _utf8mb4'anime' COLLATE utf8mb4_bin, a.anime_no,
                CONVERT(a.subject USING utf8mb4) COLLATE utf8mb4_bin, _utf8mb4'current' COLLATE utf8mb4_bin, NULL
            FROM wiki_reference ar JOIN anime a ON ar.owner_kind = 'anime' AND ar.owner_no = a.anime_no
            JOIN wiki_template t ON ar.target_kind = 'template' AND ar.target_id = CAST(t.template_no AS CHAR) AND t.deleted = FALSE
            JOIN wiki_reference tr ON tr.owner_kind = 'template' AND tr.owner_no = t.template_no
            WHERE tr.target_kind = 'image' AND tr.target_id = ?
            UNION
            SELECT _utf8mb4'anime' COLLATE utf8mb4_bin, a.anime_no,
                CONVERT(a.subject USING utf8mb4) COLLATE utf8mb4_bin, _utf8mb4'current' COLLATE utf8mb4_bin, NULL
            FROM anime a WHERE a.thumbnail = ?
        """
        private const val HISTORY_USAGE = """
            SELECT CONVERT(r.kind USING utf8mb4) COLLATE utf8mb4_bin AS kind, r.target_no,
                COALESCE(CONVERT(a.subject USING utf8mb4) COLLATE utf8mb4_bin,
                         CONVERT(r.title USING utf8mb4) COLLATE utf8mb4_bin) AS title,
                _utf8mb4'history' COLLATE utf8mb4_bin AS scope, r.revision
            FROM wiki_revision r LEFT JOIN anime a ON r.kind = 'anime' AND r.target_no = a.anime_no
            WHERE (JSON_SEARCH(r.body, 'one', ?, '!', '$**.a.src') IS NOT NULL
               OR JSON_SEARCH(r.templates, 'one', ?, '!', '$**.a.src') IS NOT NULL)
        """
    }
}
