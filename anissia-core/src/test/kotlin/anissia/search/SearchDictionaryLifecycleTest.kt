package anissia.search

import anissia.activepanel.service.ActivePanelLogService
import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*

class SearchDictionaryLifecycleTest {
    private val store = mock(SearchDictionaryStore::class.java)
    private val indexes = mock(SearchIndexService::class.java)
    private val inspector = mock(DictionaryInspector::class.java)
    private val lock = mock(SearchWorkLock::class.java)
    private val activity = mock(ActivePanelLogService::class.java)
    private val service = SearchDictionaryService(store, indexes, inspector, lock, activity)
    private val actor = Actor(an = 7, name = "제작자", roles = listOf("TRANSLATOR"))
    private val preview = DictionaryDraft(3, emptyList(), 1, 3, null, null, null, null, null)

    @Test
    fun `only the tested current candidate can enter the durable publish queue`() {
        `when`(store.draft(true)).thenReturn(preview, preview.copy(revision=4), preview.copy(job="PREPARE"),
            preview.copy(testQuery="은하", testKind="anime"))
        assertThrows<IllegalArgumentException> { service.publish(3, actor) }
        assertThrows<IllegalArgumentException> { service.markTested(3, "은하", SearchKind.ANIME, actor) }
        assertThrows<FailException> { service.publish(3, actor) }
        service.publish(3, actor)
        verify(store, times(1)).enqueue("PUBLISH", actor.name)
        verify(activity).addText("[검색] [제작자]님이 사전 초안 3 운영 적용을 요청하였습니다.", false, actor)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun `an unchanged dictionary cannot be staged as a new candidate`() {
        `when`(store.draft(true)).thenReturn(preview.copy(revision = 1))
        assertThrows<IllegalArgumentException> { service.prepare(1, actor) }
        verify(store, never()).enqueue("PREPARE", actor.name)
        verifyNoInteractions(activity)
    }

    @Test
    fun `a newer edit hides the old preview and cannot test that physical generation`() {
        `when`(store.draft(false)).thenReturn(preview.copy(revision=4, testQuery=null))
        assertNull(service.state().candidate)
        assertThrows<IllegalArgumentException> { service.targets(3) }
        verifyNoInteractions(indexes)
    }

    @Test
    fun `a successful ES switch before a server restart commits the version without rebuilding`() {
        val draft = preview.copy(job="PUBLISH", actor="관리자", testQuery="은하", testKind="anime")
        val released = SearchKind.entries.associateWith { "${it.alias}_release_v3" }
        `when`(store.hasJob()).thenReturn(true)
        `when`(store.draft(false)).thenReturn(draft)
        `when`(indexes.versionTargets(3, false)).thenReturn(released)
        `when`(indexes.activeTargets()).thenReturn(released.mapValues { listOf(it.value) })
        doAnswer { it.getArgument<() -> Boolean>(0).invoke() }.`when`(lock).run<Boolean>(org.mockito.ArgumentMatchers.any<() -> Boolean>() ?: { false })
        service.runPending()
        verify(store).published(3, "관리자")
        verify(indexes).cleanupOldReleases(setOf(1, 3))
        verify(indexes, never()).buildVersion(3, emptyList(), false)
        verifyNoInteractions(inspector)
    }

    @Test
    fun `release cleanup only deletes exact names of retired unaliased generations`() {
        val deletions = mutableListOf<String>()
        val eligible = "anissia_search_anime_release_v2"
        val found = mapOf(
            eligible to mapOf("aliases" to emptyMap<String, Any>()),
            "anissia_search_board_release_v2" to mapOf("aliases" to mapOf("retained" to emptyMap<String, Any>())),
            "anissia_search_anime_release_v3" to mapOf("aliases" to emptyMap<String, Any>()),
            "anissia_search_anime_release_v1" to mapOf("aliases" to emptyMap<String, Any>()),
            "anissia_search_anime_release_v4" to mapOf("aliases" to emptyMap<String, Any>()),
            "anissia_search_image_other_generation" to mapOf("aliases" to emptyMap<String, Any>()),
        )
        val client = object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse = when {
                method == "GET" && '*' in endpoint -> SearchResponse(200, Json.write(found))
                method == "GET" && endpoint == "/$eligible/_alias" -> SearchResponse(200, Json.write(mapOf(eligible to found.getValue(eligible))))
                method == "DELETE" -> { deletions.add(endpoint); SearchResponse(200, "{}") }
                else -> throw AssertionError("unexpected request $method $endpoint")
            }
        }
        val service = SearchIndexService(client, mock(SearchDocuments::class.java), ContentSearchRepository(client),
            mock(SearchSyncQueue::class.java), lock, false)
        service.cleanupOldReleases(setOf(1, 3))
        assertEquals(listOf("/$eligible"), deletions)
    }

    @Test
    fun `rule additions and deletions include the actual rule and actor in private common history`() {
        val rule = DictionaryRule("id", "synonyms", "초전자포", "레일건")
        `when`(store.draft(true)).thenReturn(preview, preview.copy(rules = listOf(rule)))
        service.add("synonyms", "초전자포", "레일건", 3, actor)
        service.delete("synonyms", "id", 3, actor)
        verify(activity).addText("[검색] [제작자]님이 동의어 [초전자포] → [레일건] 규칙을 초안에 추가하였습니다.", false, actor)
        verify(activity).addText("[검색] [제작자]님이 동의어 [초전자포] → [레일건] 규칙을 초안에서 삭제하였습니다.", false, actor)
        verifyNoMoreInteractions(activity)
        verifyNoInteractions(indexes)
    }

    @Test
    fun `repeated successful comparisons do not duplicate completion history`() {
        `when`(store.draft(true)).thenReturn(preview, preview.copy(testQuery = "은하", testKind = "anime"))
        service.markTested(3, "은하", SearchKind.ANIME, actor)
        service.markTested(3, "은하", SearchKind.ANIME, actor)
        verify(activity).addText("[검색] [제작자]님이 사전 초안 3 비교 테스트를 완료하였습니다. 검색어: [은하]", false, actor)
        verifyNoMoreInteractions(activity)
    }

    @Test
    fun `a reset rebuild uses active rules instead of unpublished draft changes`() {
        val active = listOf(DictionaryRule("active", "user-dictionary", "운영사전", ""))
        val unpublished = listOf(DictionaryRule("draft", "user-dictionary", "미적용초안", ""))
        `when`(store.draft(false)).thenReturn(preview.copy(rules = unpublished))
        `when`(store.activeRules()).thenReturn(active)
        doAnswer { it.getArgument<() -> Map<String, Int>>(0).invoke() }.`when`(lock).run<Map<String, Int>>(
            org.mockito.ArgumentMatchers.any<() -> Map<String, Int>>() ?: { emptyMap() })
        val definitions = mutableListOf<String>()
        val client = object : SearchClient {
            override fun request(method: String, endpoint: String, body: String?): SearchResponse = when {
                endpoint.startsWith("/_alias/") -> SearchResponse(404, "{}")
                endpoint == "/_aliases" -> SearchResponse(200, """{"acknowledged":true}""")
                method == "PUT" && !endpoint.endsWith("/_settings") -> {
                    definitions.add(requireNotNull(body)); SearchResponse(200, "{}")
                }
                endpoint.endsWith("/_count") -> SearchResponse(200, """{"count":0}""")
                else -> SearchResponse(200, "{}")
            }
        }
        val service = SearchIndexService(client, mock(SearchDocuments::class.java), ContentSearchRepository(client),
            mock(SearchSyncQueue::class.java), lock, false, store)
        service.rebuild(true)
        assertEquals(4, definitions.size)
        definitions.forEach { definition ->
            assertEquals(listOf("운영사전"), Json.read<List<String>>(Json.readTree(definition).path("settings").path("analysis")
                .path("tokenizer").path("korean").path("user_dictionary_rules").toString()))
        }
    }
}
