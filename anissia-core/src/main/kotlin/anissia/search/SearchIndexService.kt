package anissia.search

import anissia.external.search.SearchClient
import anissia.support.FailException
import anissia.support.Json
import anissia.support.logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SearchIndexService @Autowired constructor(
    private val client: SearchClient,
    private val documents: SearchDocuments,
    private val repository: ContentSearchRepository,
    private val queue: SearchSyncQueue,
    private val workLock: SearchWorkLock,
    @Value("\${anissia.search.worker-enabled:true}") private val workerEnabled: Boolean,
    private val dictionaryStore: SearchDictionaryStore?,
) {
    constructor(client: SearchClient, documents: SearchDocuments, repository: ContentSearchRepository, queue: SearchSyncQueue, workLock: SearchWorkLock, workerEnabled: Boolean)
        : this(client, documents, repository, queue, workLock, workerEnabled, null)

    private val log = logger<SearchIndexService>()

    @Scheduled(fixedDelayString = "\${anissia.search.sync-delay-ms:1000}")
    fun syncPending() {
        if (!workerEnabled) return
        try {
            workLock.run {
                queue.pending().forEach { task ->
                    try {
                        check(client.request("HEAD", "/_alias/${task.kind.alias}").status == 200) { "Search index is not initialized" }
                        repository.write(task.kind, task.id, documents.load(task.kind, task.id))
                        queue.complete(task)
                    } catch (e: Exception) {
                        queue.retry(task)
                        log.warn("search sync failed type={} id={} reason={}", task.kind.key, task.id, e.message)
                    }
                }
            }
        } catch (e: Exception) { log.error("search queue failed: {}", e.message) }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @JvmOverloads
    fun rebuild(reset: Boolean, onProgress: (SearchKind, Int) -> Unit = { _, _ -> }): Map<String, Int> = workLock.run { rebuildLocked(reset, onProgress) }
        ?: throw FailException("검색엔진 갱신 작업이 진행 중입니다. 잠시 후 다시 시도해 주세요.")

    private fun rebuildLocked(reset: Boolean, onProgress: (SearchKind, Int) -> Unit): Map<String, Int> {
        val draft = dictionaryStore?.draft()
        if (draft?.job != null) throw FailException("사전 적용 작업을 완료한 후 다시 시도해 주세요.")
        val generation = UUID.randomUUID().toString().replace("-", "")
        val targets = SearchKind.entries.associateWith { "${it.alias}_$generation" }
        val previous = activeTargets()
        val defaultDefinition by lazy {
            if (draft != null && draft.active > 0) versionDefinition(draft.active, requireNotNull(dictionaryStore).rules(draft.active))
            else ContentSearchRepository.MAPPING
        }
        val counts = build(targets, onProgress) { kind ->
            if (reset || previous.getValue(kind).isEmpty()) defaultDefinition else currentDefinition(kind)
        }
        switch(previous, targets)
        return counts
    }

    internal fun versionTargets(version: Long, preview: Boolean): Map<SearchKind, String> {
        require(version >= 0)
        val purpose = if (preview) "preview" else "release"
        return SearchKind.entries.associateWith { "${it.alias}_${purpose}_v$version" }
    }

    internal fun activeTargets(): Map<SearchKind, List<String>> = SearchKind.entries.associateWith { kind ->
        val alias = client.request("GET", "/_alias/${kind.alias}")
        check(alias.status == 200 || alias.status == 404) { "Cannot inspect search alias: ${kind.key}" }
        if (alias.status == 200) Json.readTree(alias.body).propertyNames().toList() else emptyList()
    }

    internal fun buildVersion(version: Long, rules: List<DictionaryRule>, preview: Boolean) {
        val targets = versionTargets(version, preview)
        val definition = versionDefinition(version, rules)
        discardDetached(targets)
        val live = activeTargets()
        build(targets, { _, _ -> }) { kind ->
            if (live.getValue(kind).isEmpty()) definition else {
                val settings = Json.read<Map<String, Any>>(Json.readTree(currentDefinition(kind)).path("settings").toString()) - "analysis"
                val base = Json.read<Map<String, Any>>(definition)
                val analysisSettings = Json.read<Map<String, Any>>(Json.readTree(definition).path("settings").toString())
                Json.write(base + ("settings" to (settings + analysisSettings)))
            }
        }
    }

    private fun versionDefinition(version: Long, rules: List<DictionaryRule>): String {
        val definition = Json.readTree(DictionaryAnalysis.definition(rules)) as tools.jackson.databind.node.ObjectNode
        definition.withObjectProperty("mappings").withObjectProperty("_meta").put("anissia_dictionary_version", version)
        return definition.toString()
    }

    internal fun discardDetached(targets: Map<SearchKind, String>) {
        targets.forEach { (kind, target) ->
            require(target.matches(Regex("${kind.alias}_(preview|release)_v[0-9]+")))
            val aliases = client.request("GET", "/$target/_alias")
            if (aliases.status != 404) {
                check(aliases.status == 200 && Json.readTree(aliases.body).path(target).path("aliases").let { it.isObject && it.isEmpty }) { "색인에 연결된 별칭을 확인해 주세요." }
                checked("DELETE", "/$target")
            }
        }
    }

    internal fun cleanupOldReleases(versions: List<Long>) {
        if (versions.isEmpty()) return
        val eligible = versions.flatMap { versionTargets(it, preview = false).entries }.associate { it.value to it.key }
        val patterns = SearchKind.entries.joinToString(",") { "${it.alias}_release_v*" }
        val existing = Json.readTree(checked("GET", "/$patterns/_alias?allow_no_indices=true&ignore_unavailable=true"))
        existing.properties().forEach { (name, definition) ->
            val kind = eligible[name]
            if (kind != null && definition.path("aliases").let { it.isObject && it.isEmpty }) {
                discardDetached(mapOf(kind to name))
            }
        }
    }

    private fun build(targets: Map<SearchKind, String>, onProgress: (SearchKind, Int) -> Unit, definitionFor: (SearchKind) -> String): Map<String, Int> {
        val started = System.nanoTime()
        val counts = linkedMapOf<String, Int>()
        targets.forEach { (kind, target) ->
            val definition = definitionFor(kind)
            val original = Json.read<Map<String, Any>>(definition)
            val settings = Json.readTree(definition).path("settings").let { if (it.has("index")) it.path("index") else it }
            val originalSettings = Json.read<Map<String, Any>>(settings.toString())
            checked("PUT", "/$target", Json.write(original + ("settings" to (originalSettings + mapOf("refresh_interval" to "-1", "number_of_replicas" to 0)))))
            var after = if (kind == SearchKind.IMAGE) "" else "0"
            var count = 0
            while (true) {
                val ids = documents.ids(kind, after)
                if (ids.isEmpty()) break
                val batch = documents.loadBatch(kind, ids)
                repository.writeBatch(kind, batch, target)
                count += batch.size
                after = ids.last()
            }
            checked("PUT", "/$target/_settings", Json.write(mapOf(
                "index" to mapOf("refresh_interval" to originalSettings["refresh_interval"], "number_of_replicas" to originalSettings["number_of_replicas"]),
            )))
            checked("POST", "/$target/_refresh")
            val actual = Json.readTree(checked("GET", "/$target/_count")).path("count").asInt(-1)
            check(actual == count) { "Search count mismatch: ${kind.key} expected=$count actual=$actual" }
            counts[kind.key] = count
            log.info("search rebuild progress type={} documents={} elapsedMs={}", kind.key, count, (System.nanoTime() - started) / 1_000_000)
            onProgress(kind, count)
        }
        return counts
    }

    internal fun switch(previous: Map<SearchKind, List<String>>, targets: Map<SearchKind, String>) {
        val actions = SearchKind.entries.flatMap { kind ->
            previous.getValue(kind).map { mapOf("remove" to mapOf("index" to it, "alias" to kind.alias, "must_exist" to true)) } +
                listOf(mapOf("add" to mapOf("index" to targets.getValue(kind), "alias" to kind.alias)))
        }
        val swapped = Json.readTree(checked("POST", "/_aliases", Json.write(mapOf("actions" to actions))))
        check(swapped.path("acknowledged").asBoolean(false) && !swapped.path("errors").asBoolean(false)) { "Search alias switch was not acknowledged; inspect aliases before retrying" }
        log.info("search aliases switched: {}", targets)
    }

    private fun currentDefinition(kind: SearchKind): String {
        val current = Json.readTree(checked("GET", "/${kind.alias}")).properties().first().value
        val settings = current.path("settings").path("index")
        val kept = Json.objectNode()
        listOf("analysis", "number_of_shards", "number_of_replicas", "refresh_interval", "max_result_window").forEach { name -> settings.get(name)?.let { kept.set(name, it) } }
        return Json.write(mapOf("settings" to kept, "mappings" to current.path("mappings")))
    }

    private fun checked(method: String, path: String, body: String? = null): String {
        val response = client.request(method, path, body)
        if (response.status !in 200..299) {
            log.warn("search operation failed method={} path={} status={} response={}", method, path, response.status, response.body.take(2000))
            val reason = runCatching { Json.readTree(response.body).path("error").path("reason").asString("") }.getOrDefault("")
            throw IllegalStateException("검색엔진 작업 실패 (HTTP ${response.status}) ${reason.take(300)}")
        }
        return response.body
    }
}
