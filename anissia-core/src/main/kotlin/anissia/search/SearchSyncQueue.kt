package anissia.search

import anissia.anime.service.AnimeDocumentChangedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource

class SearchChangedEvent(val kind: SearchKind, val ids: Collection<String>) {
    constructor(kind: SearchKind, id: Any) : this(kind, listOf(id.toString()))
}

data class SearchSyncTask(val kind: SearchKind, val id: String, val revision: Long)

@Service
class SearchSyncQueue(private val jdbc: JdbcTemplate) {
    @Transactional
    @EventListener
    fun onChange(event: SearchChangedEvent) {
        event.ids.sorted().forEach { id ->
            mark(event.kind, id)
            if (event.kind == SearchKind.TEMPLATE) {
                jdbc.query("SELECT DISTINCT owner_no FROM wiki_reference WHERE owner_kind = 'anime' AND target_kind = 'template' AND target_id = ? ORDER BY owner_no", { r, _ -> r.getString(1) }, id)
                    .forEach { mark(SearchKind.ANIME, it) }
            }
        }
    }

    @Transactional
    @EventListener
    fun onAnimeChange(event: AnimeDocumentChangedEvent) {
        event.animeNos.sorted().forEach { mark(SearchKind.ANIME, it.toString()) }
    }

    private fun mark(kind: SearchKind, id: String) {
        jdbc.update("""INSERT INTO search_sync_task (kind, entity_id, revision, attempts, next_at) VALUES (?, ?, 1, 0, CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE revision = revision + 1, attempts = 0, next_at = CURRENT_TIMESTAMP(6)""", kind.key, id)
    }

    fun pending(): List<SearchSyncTask> = jdbc.query("""SELECT kind, entity_id, revision FROM search_sync_task
        WHERE next_at <= CURRENT_TIMESTAMP(6) ORDER BY next_at, kind, entity_id LIMIT 100""", { row, _ ->
        SearchSyncTask(SearchKind.entries.first { it.key == row.getString(1) }, row.getString(2), row.getLong(3))
    })

    fun complete(task: SearchSyncTask) {
        jdbc.update("DELETE FROM search_sync_task WHERE kind = ? AND entity_id = ? AND revision = ?", task.kind.key, task.id, task.revision)
    }

    fun retry(task: SearchSyncTask) {
        jdbc.update("""UPDATE search_sync_task SET attempts = attempts + 1,
            next_at = TIMESTAMPADD(SECOND, LEAST(300, POW(2, LEAST(attempts, 8))), CURRENT_TIMESTAMP(6))
            WHERE kind = ? AND entity_id = ? AND revision = ?""", task.kind.key, task.id, task.revision)
    }
}

@Service
class SearchWorkLock(private val dataSource: DataSource) {
    fun <T : Any> run(action: () -> T): T? = dataSource.connection.use { connection ->
        val name = "anissia_search_${connection.catalog}"
        val acquired = connection.prepareStatement("SELECT GET_LOCK(?, 0)").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { it.next() && it.getInt(1) == 1 }
        }
        if (!acquired) return null
        try { action() } finally {
            connection.prepareStatement("SELECT RELEASE_LOCK(?)").use { statement -> statement.setString(1, name); statement.execute() }
        }
    }
}
