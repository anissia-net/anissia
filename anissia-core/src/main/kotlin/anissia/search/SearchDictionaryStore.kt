package anissia.search

import anissia.support.FailException
import anissia.support.Json
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

internal data class DictionaryDraft(
    val revision: Long, val rules: List<DictionaryRule>, val active: Long, val candidate: Long?,
    val testQuery: String?, val testKind: String?, val job: String?, val actor: String?, val error: String?,
)

data class DictionaryVersion(val version: Long, val createdAt: String, val actor: String)

@Repository
class SearchDictionaryStore(private val jdbc: JdbcTemplate) {
    internal fun draft(lock: Boolean = false): DictionaryDraft = requireNotNull(jdbc.queryForObject(
        "SELECT * FROM search_dictionary_state WHERE id = 1" + if (lock) " FOR UPDATE" else "",
        { row, _ -> DictionaryDraft(row.getLong("revision"), Json.read(row.getString("draft")), row.getLong("active_version"),
            (row.getObject("candidate_version") as? Number)?.toLong(), row.getString("tested_query"), row.getString("tested_kind"),
            row.getString("job"), row.getString("job_actor"), row.getString("error_message")) },
    ))

    fun hasJob(): Boolean = jdbc.queryForObject("SELECT job IS NOT NULL FROM search_dictionary_state WHERE id=1", Boolean::class.java) == true

    fun rules(version: Long): List<DictionaryRule> {
        val snapshot = jdbc.query("SELECT rules FROM search_dictionary_version WHERE version=?", { row, _ -> row.getString(1) }, version).singleOrNull()
            ?: throw FailException("사전 버전을 찾을 수 없습니다.")
        return Json.read(snapshot)
    }

    fun history(): List<DictionaryVersion> = jdbc.query("SELECT version, published_at, actor FROM search_dictionary_version WHERE published_at IS NOT NULL ORDER BY published_at DESC, version DESC",
        { row, _ -> DictionaryVersion(row.getLong(1), row.getTimestamp(2).toLocalDateTime().toString(), row.getString(3)) })

    internal fun saveDraft(rules: List<DictionaryRule>) {
        jdbc.update("UPDATE search_dictionary_state SET draft=?, revision=revision+1, tested_query=NULL, tested_kind=NULL, error_message=NULL WHERE id=1", Json.write(rules))
    }

    internal fun enqueue(job: String, actor: String) {
        jdbc.update("UPDATE search_dictionary_state SET job=?, job_actor=?, error_message=NULL WHERE id=1", job, actor)
    }

    internal fun saveSnapshot(draft: DictionaryDraft) {
        jdbc.update("INSERT INTO search_dictionary_version(version, rules, actor) VALUES (?,?,?) ON DUPLICATE KEY UPDATE version=VALUES(version)", draft.revision, Json.write(draft.rules), draft.actor.orEmpty())
    }

    internal fun prepared(version: Long) {
        jdbc.update("UPDATE search_dictionary_state SET candidate_version=?, tested_query=NULL, tested_kind=NULL, job=NULL, job_actor=NULL, error_message=NULL WHERE id=1", version)
    }

    internal fun tested(q: String, kind: SearchKind) {
        jdbc.update("UPDATE search_dictionary_state SET tested_query=?, tested_kind=?, error_message=NULL WHERE id=1", q, kind.key)
    }

    @Transactional
    internal fun published(version: Long, actor: String) {
        jdbc.update("UPDATE search_dictionary_version SET published_at=CURRENT_TIMESTAMP(6), actor=? WHERE version=?", actor, version)
        jdbc.update("UPDATE search_dictionary_state SET active_version=?, candidate_version=NULL, tested_query=NULL, tested_kind=NULL, job=NULL, job_actor=NULL, error_message=NULL WHERE id=1", version)
    }

    internal fun failed(message: String) {
        jdbc.update("UPDATE search_dictionary_state SET job=NULL, job_actor=NULL, error_message=? WHERE id=1", message.take(600))
    }
}
