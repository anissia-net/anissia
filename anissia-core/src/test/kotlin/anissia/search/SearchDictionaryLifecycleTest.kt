package anissia.search

import anissia.external.search.SearchClient
import anissia.external.search.SearchResponse
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
    private val service = SearchDictionaryService(store, indexes, inspector, lock)
    private val preview = DictionaryDraft(3, emptyList(), 1, 3, null, null, null, null, null)

    @Test
    fun `only the tested current candidate can enter the durable publish queue`() {
        `when`(store.draft(true)).thenReturn(preview, preview.copy(revision=4), preview.copy(job="PREPARE"),
            preview.copy(testQuery="은하", testKind="anime"))
        assertThrows<IllegalArgumentException> { service.publish(3, "관리자") }
        assertThrows<IllegalArgumentException> { service.markTested(3, "은하", SearchKind.ANIME) }
        assertThrows<FailException> { service.publish(3, "관리자") }
        service.publish(3, "관리자")
        verify(store, times(1)).enqueue("PUBLISH", "관리자")
    }

    @Test
    fun `an unchanged dictionary cannot be staged as a new candidate`() {
        `when`(store.draft(true)).thenReturn(preview.copy(revision = 1))
        assertThrows<IllegalArgumentException> { service.prepare(1, "관리자") }
        verify(store, never()).enqueue("PREPARE", "관리자")
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
        service.cleanupOldReleases(listOf(0, 2))
        assertEquals(listOf("/$eligible"), deletions)
    }

    @Test
    fun `restoring old rules only changes draft and requires a fresh preview`() {
        val rules = listOf(DictionaryRule("id", "stopwords", "에서", ""))
        `when`(store.draft(true)).thenReturn(preview)
        `when`(store.history()).thenReturn(listOf(DictionaryVersion(0, "", "초기 사전"), DictionaryVersion(1, "", "관리자")))
        `when`(store.rules(1)).thenReturn(rules)
        service.restore(1, 3)
        verify(store).saveDraft(rules)
        verifyNoInteractions(indexes)
    }
}
