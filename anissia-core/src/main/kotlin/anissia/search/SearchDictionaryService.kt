package anissia.search

import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class DictionaryState(
    val revision: Long, val active: Long, val candidate: Long?, val tested: Boolean,
    val busy: Boolean, val error: String?,
)

data class DictionaryPage(val items: List<DictionaryRule>, val total: Int)

@Service
class SearchDictionaryService(
    private val store: SearchDictionaryStore,
    private val indexes: SearchIndexService,
    private val inspector: DictionaryInspector,
    private val workLock: SearchWorkLock,
    private val activity: ActivePanelLogService,
) {
    private val log = logger<SearchDictionaryService>()
    private val categories = setOf("user-dictionary", "synonyms", "corrections", "stopwords")

    @Transactional(readOnly = true)
    fun state(): DictionaryState {
        val draft = store.draft()
        return DictionaryState(draft.revision, draft.active, draft.candidate?.takeIf { it == draft.revision }, draft.testQuery != null,
            draft.job != null, draft.error)
    }

    fun rules(category: String, q: String, page: Int): DictionaryPage {
        require(category in categories && q.length <= 300 && page in 0..100_000) { "사전 종류와 검색 조건을 확인해 주세요." }
        val term = q.trim()
        val matches = store.draft().rules.filter { it.category == category && (it.term.contains(term, true) || it.value.contains(term, true)) }
            .sortedWith(compareBy({ it.term }, { it.id }))
        return DictionaryPage(matches.drop(page * 30).take(30), matches.size)
    }

    @Transactional
    fun add(category: String, term: String, value: String, revision: Long, actor: Actor) {
        val rule = DictionaryRule(UUID.randomUUID().toString(), category, term.trim(), value.trim())
        DictionaryAnalysis.validate(rule)
        val draft = editable(revision)
        require(draft.rules.size < 10_000) { "사전 항목은 10,000개까지 등록할 수 있습니다." }
        require(draft.rules.none { it.category == category && it.term.equals(rule.term, true) }) { "이미 등록된 표제어입니다." }
        store.saveDraft(draft.rules + rule)
        activity.addText("[검색] [${actor.name}]님이 ${describe(rule)} 규칙을 초안에 추가하였습니다.", actor = actor)
    }

    @Transactional
    fun delete(category: String, id: String, revision: Long, actor: Actor) {
        val draft = editable(revision)
        val rule = requireNotNull(draft.rules.find { it.category == category && it.id == id }) { "삭제할 항목을 찾을 수 없습니다." }
        store.saveDraft(draft.rules - rule)
        activity.addText("[검색] [${actor.name}]님이 ${describe(rule)} 규칙을 초안에서 삭제하였습니다.", actor = actor)
    }

    @Transactional
    fun prepare(revision: Long, actor: Actor) {
        val draft = editable(revision)
        require(draft.active != revision) { "운영 사전과 같은 버전입니다. 먼저 초안을 변경해 주세요." }
        if (draft.candidate == revision) return
        store.enqueue("PREPARE", actor.name)
        activity.addText("[검색] [${actor.name}]님이 사전 초안 $revision 테스트 버전 준비를 요청하였습니다.", actor = actor)
    }

    fun targets(version: Long): Map<SearchKind, String> {
        val draft = store.draft()
        require(draft.candidate == version && draft.revision == version && draft.job == null) { "이 초안을 테스트에 먼저 적용해 주세요." }
        return indexes.versionTargets(version, preview = true)
    }

    @Transactional
    fun markTested(version: Long, q: String, kind: SearchKind, actor: Actor) {
        val draft = editable(version)
        require(draft.candidate == version) { "후보 버전이 변경되었습니다. 다시 테스트해 주세요." }
        store.tested(q, kind)
        if (draft.testQuery == null) {
            activity.addText("[검색] [${actor.name}]님이 사전 초안 $version 비교 테스트를 완료하였습니다. 검색어: [$q]", actor = actor)
        }
    }

    @Transactional
    fun publish(version: Long, actor: Actor) {
        val draft = editable(version)
        require(draft.candidate == version && draft.testQuery != null && draft.active != version) { "변경한 사전을 간접 적용하고 테스트해 주세요." }
        store.enqueue("PUBLISH", actor.name)
        activity.addText("[검색] [${actor.name}]님이 사전 초안 $version 운영 적용을 요청하였습니다.", actor = actor)
    }

    private fun describe(rule: DictionaryRule): String {
        val category = when (rule.category) {
            "user-dictionary" -> "사용자 사전"
            "synonyms" -> "동의어"
            "corrections" -> "교정어"
            else -> "불용어"
        }
        return "$category [${rule.term}]" + if (rule.value.isBlank()) "" else " → [${rule.value}]"
    }

    private fun editable(revision: Long): DictionaryDraft {
        val draft = store.draft(lock = true)
        if (draft.job != null) throw FailException("사전 적용 작업이 진행 중입니다.")
        require(draft.revision == revision) { "초안이 변경되었습니다. 새로고침 후 다시 시도해 주세요." }
        return draft
    }

    @Scheduled(fixedDelayString = "\${anissia.search.dictionary-delay-ms:1500}")
    fun runPending() {
        if (!store.hasJob()) return
        workLock.run {
            val draft = store.draft()
            when (draft.job) {
                "PREPARE" -> prepareCandidate(draft)
                "PUBLISH" -> publishCandidate(draft)
            }
            true
        }
    }

    private fun prepareCandidate(draft: DictionaryDraft) {
        try {
            indexes.buildVersion(draft.revision, draft.rules, preview = true)
        } catch (e: Exception) {
            log.error("dictionary preparation failed version={}", draft.revision, e)
            store.failed(e.message ?: "후보 인덱스 구축에 실패했습니다.")
            cleanupPreview(draft.revision)
            return
        }
        store.prepared(draft.revision)
        draft.candidate?.takeIf { it != draft.revision }?.let(::cleanupPreview)
    }

    private fun publishCandidate(draft: DictionaryDraft) {
        val version = draft.revision
        val targets = indexes.versionTargets(version, preview = false)
        fun switched() = indexes.activeTargets().all { (kind, physical) -> physical == listOf(targets.getValue(kind)) }
        try {
            // A server may have stopped between the Elasticsearch switch and the DB commit.
            if (!switched()) {
                indexes.buildVersion(version, draft.rules, preview = false)
                val kind = SearchKind.entries.single { it.key == draft.testKind }
                inspector.inspect(kind, targets.getValue(kind), requireNotNull(draft.testQuery))
                indexes.switch(indexes.activeTargets(), targets)
            }
        } catch (e: Exception) {
            log.error("dictionary publication failed version={}", version, e)
            // Leave the durable job queued when the outcome cannot yet be determined.
            if (!switched()) {
                store.failed(e.message ?: "운영 적용에 실패했습니다.")
                runCatching { indexes.discardDetached(targets) }.onFailure { log.warn("unused release cleanup failed", it) }
                return
            }
        }
        store.published(version, requireNotNull(draft.actor))
        cleanupPreview(version)
        runCatching { indexes.cleanupOldReleases(setOf(version, draft.active)) }
            .onFailure { log.warn("old dictionary release cleanup failed", it) }
    }

    private fun cleanupPreview(version: Long) {
        runCatching { indexes.discardDetached(indexes.versionTargets(version, preview = true)) }
            .onFailure { log.warn("unused preview cleanup failed version={}", version, it) }
    }
}
