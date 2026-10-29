package anissia.search

import anissia.external.search.SearchClient
import anissia.support.FailException
import anissia.support.Json
import anissia.support.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SearchIndexService(
    private val client: SearchClient,
    private val documents: SearchDocuments,
    private val repository: ContentSearchRepository,
    private val queue: SearchSyncQueue,
    private val workLock: SearchWorkLock,
    @Value("\${anissia.search.worker-enabled:true}") private val workerEnabled: Boolean,
) {
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
    fun rebuild(reset: Boolean): Map<String, Int> = workLock.run { rebuildLocked(reset) }
        ?: throw FailException("검색엔진 갱신 작업이 진행 중입니다. 잠시 후 다시 시도해 주세요.")

    private fun rebuildLocked(reset: Boolean): Map<String, Int> {
        val generation = UUID.randomUUID().toString().replace("-", "")
        val targets = SearchKind.entries.associateWith { "${it.alias}_$generation" }
        val counts = linkedMapOf<String, Int>()
        val previous = linkedMapOf<SearchKind, List<String>>()
        SearchKind.entries.forEach { kind ->
            val alias = client.request("GET", "/_alias/${kind.alias}")
            check(alias.status == 200 || alias.status == 404) { "Cannot inspect search alias: ${kind.key}" }
            previous[kind] = if (alias.status == 200) Json.readTree(alias.body).propertyNames().toList() else emptyList()
        }
        targets.forEach { (kind, target) ->
            val definition = if (reset || previous.getValue(kind).isEmpty()) ContentSearchRepository.MAPPING else currentDefinition(kind)
            checked("PUT", "/$target", definition)
            var after = if (kind == SearchKind.IMAGE) "" else "0"
            var count = 0
            while (true) {
                val ids = documents.ids(kind, after)
                if (ids.isEmpty()) break
                ids.forEach { id -> documents.load(kind, id)?.let { repository.write(kind, id, it, target); count++ } }
                after = ids.last()
            }
            checked("POST", "/$target/_refresh")
            val actual = Json.readTree(checked("GET", "/$target/_count")).path("count").asInt(-1)
            check(actual == count) { "Search count mismatch: ${kind.key} expected=$count actual=$actual" }
            counts[kind.key] = count
        }
        val actions = SearchKind.entries.flatMap { kind ->
            previous.getValue(kind).map { mapOf("remove" to mapOf("index" to it, "alias" to kind.alias, "must_exist" to true)) } +
                listOf(mapOf("add" to mapOf("index" to targets.getValue(kind), "alias" to kind.alias)))
        }
        val swapped = Json.readTree(checked("POST", "/_aliases", Json.write(mapOf("actions" to actions))))
        check(swapped.path("acknowledged").asBoolean(false) && !swapped.path("errors").asBoolean(false)) { "Search alias switch was not acknowledged; inspect aliases before retrying" }
        log.info("search rebuild reset={} counts={}", reset, counts)
        return counts
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
        check(response.status in 200..299) { "Search operation failed: $method $path status=${response.status}" }
        return response.body
    }
}
