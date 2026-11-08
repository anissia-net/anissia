package anissia.search

import anissia.activepanel.service.ActivePanelLogService
import anissia.support.Json
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

internal data class DictionaryDraft(
    val revision: Long, val rules: List<DictionaryRule>, val active: Long, val candidate: Long?,
    val testQuery: String?, val testKind: String?, val job: String?, val actor: String?, val error: String?,
)

@Repository
class SearchDictionaryStore(private val jdbc: JdbcTemplate, private val activity: ActivePanelLogService) {
    internal fun draft(lock: Boolean = false): DictionaryDraft = requireNotNull(jdbc.queryForObject(
        "SELECT * FROM search_dictionary_state WHERE id = 1" + if (lock) " FOR UPDATE" else "",
        { row, _ -> DictionaryDraft(row.getLong("revision"), Json.read(row.getString("draft")), row.getLong("active_version"),
            (row.getObject("candidate_version") as? Number)?.toLong(), row.getString("tested_query"), row.getString("tested_kind"),
            row.getString("job"), row.getString("job_actor"), row.getString("error_message")) },
    ))

    fun hasJob(): Boolean = jdbc.queryForObject("SELECT job IS NOT NULL FROM search_dictionary_state WHERE id=1", Boolean::class.java) == true

    fun activeRules(): List<DictionaryRule> = Json.read(requireNotNull(
        jdbc.queryForObject("SELECT active_rules FROM search_dictionary_state WHERE id=1", String::class.java),
    ))

    internal fun saveDraft(rules: List<DictionaryRule>) {
        jdbc.update("UPDATE search_dictionary_state SET draft=?, revision=revision+1, tested_query=NULL, tested_kind=NULL, error_message=NULL WHERE id=1", Json.write(rules))
    }

    internal fun enqueue(job: String, actor: String) {
        jdbc.update("UPDATE search_dictionary_state SET job=?, job_actor=?, error_message=NULL WHERE id=1", job, actor)
    }

    @Transactional
    internal fun prepared(version: Long) {
        val draft = draft(lock = true)
        jdbc.update("UPDATE search_dictionary_state SET candidate_version=?, tested_query=NULL, tested_kind=NULL, job=NULL, job_actor=NULL, error_message=NULL WHERE id=1", version)
        activity.addText("[검색] [${draft.actor}]님이 요청한 사전 초안 $version 테스트 버전 준비가 완료되었습니다.")
    }

    internal fun tested(q: String, kind: SearchKind) {
        jdbc.update("UPDATE search_dictionary_state SET tested_query=?, tested_kind=?, error_message=NULL WHERE id=1", q, kind.key)
    }

    @Transactional
    internal fun published(version: Long, actor: String) {
        jdbc.update("UPDATE search_dictionary_state SET active_version=?, active_rules=draft, candidate_version=NULL, tested_query=NULL, tested_kind=NULL, job=NULL, job_actor=NULL, error_message=NULL WHERE id=1", version)
        activity.addText("[검색] [$actor]님이 사전 버전 $version 운영 적용을 완료하였습니다.")
    }

    @Transactional
    internal fun failed(message: String) {
        val draft = draft(lock = true)
        jdbc.update("UPDATE search_dictionary_state SET job=NULL, job_actor=NULL, error_message=? WHERE id=1", message.take(600))
        val task = if (draft.job == "PUBLISH") "운영 적용" else "테스트 버전 준비"
        activity.addText("[검색] [${draft.actor}]님이 요청한 사전 초안 ${draft.revision} $task 작업에 실패하였습니다. ${message.take(600)}")
    }
}
