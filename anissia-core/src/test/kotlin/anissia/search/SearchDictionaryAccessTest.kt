package anissia.search

import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import anissia.security.ActorArgumentResolver
import anissia.security.DatAuthentication
import anissia.support.GlobalExceptionHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class SearchDictionaryAccessTest {
    private val service = mock(SearchDictionaryService::class.java)
    private val inspector = mock(DictionaryInspector::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(SearchDictionaryController(service, inspector))
        .setCustomArgumentResolvers(ActorArgumentResolver())
        .setControllerAdvice(GlobalExceptionHandler()).build()

    @AfterEach
    fun clearActor() = SecurityContextHolder.clearContext()

    @Test
    fun `root and translators can edit prepare test and publish search rules`() {
        val inspected = SearchInspection(DictionaryTokens("은하", "은하", listOf("은하"), listOf("은하")), listOf(DictionaryHit("1", "은하")), 1)
        `when`(service.targets(3)).thenReturn(mapOf(SearchKind.ANIME to "candidate"))
        `when`(inspector.inspect(SearchKind.ANIME, SearchKind.ANIME.alias, "은하")).thenReturn(inspected)
        `when`(inspector.inspect(SearchKind.ANIME, "candidate", "은하")).thenReturn(inspected)
        for (role in listOf("ROOT", "TRANSLATOR")) {
            val actor = Actor(an = 7, name = role, roles = listOf(role))
            SecurityContextHolder.getContext().authentication = DatAuthentication(actor)
            for ((path, body) in listOf(
                "/rules/stopwords" to """{"term":"에서","revision":3}""",
                "/prepare" to """{"revision":3}""",
                "/test" to """{"q":"은하","kind":"anime","version":3}""",
                "/publish" to """{"version":3}""",
            )) mvc.perform(post("/admin/search$path").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("ok"))
            mvc.perform(delete("/admin/search/rules/stopwords/id").param("revision", "3"))
                .andExpect(status().isOk).andExpect(jsonPath("$.code").value("ok"))
            verify(service).add("stopwords", "에서", "", 3, actor)
            verify(service).delete("stopwords", "id", 3, actor)
            verify(service).prepare(3, actor)
            verify(service).markTested(3, "은하", SearchKind.ANIME, actor)
            verify(service).publish(3, actor)
        }
    }

    @Test
    fun `anonymous and normal members cannot read or mutate search administration`() {
        for (actor in listOf(Actor(), Actor(an = 7))) {
            SecurityContextHolder.getContext().authentication = DatAuthentication(actor)
            val requests = listOf(
                get("/admin/search/state"),
                get("/admin/search/rules/stopwords"),
                post("/admin/search/rules/stopwords").contentType(MediaType.APPLICATION_JSON).content("""{"term":"에서","revision":3}"""),
                delete("/admin/search/rules/stopwords/id").param("revision", "3"),
                post("/admin/search/prepare").contentType(MediaType.APPLICATION_JSON).content("""{"revision":3}"""),
                post("/admin/search/test").contentType(MediaType.APPLICATION_JSON).content("""{"q":"은하","kind":"anime","version":3}"""),
                post("/admin/search/publish").contentType(MediaType.APPLICATION_JSON).content("""{"version":3}"""),
            )
            requests.forEach { request -> mvc.perform(request).andExpect(jsonPath("$.code").value("error"))
                .andExpect(jsonPath("$.message").value("권한이 없습니다.")) }
        }
        verifyNoInteractions(service, inspector)
    }

    @Test
    fun `state no longer exposes history and the restore endpoint is removed`() {
        SecurityContextHolder.getContext().authentication = DatAuthentication(Actor(an = 7, roles = listOf("ROOT")))
        `when`(service.state()).thenReturn(DictionaryState(3, 1, 3, true, false, null))
        mvc.perform(get("/admin/search/state")).andExpect(jsonPath("$.data.active").value(1))
            .andExpect(jsonPath("$.data.history").doesNotExist())
        mvc.perform(post("/admin/search/restore").contentType(MediaType.APPLICATION_JSON).content("""{"version":1,"revision":3}"""))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `private common history is visible only to root and translator admins`() {
        val repository = mock(ActivePanelRepository::class.java)
        val rows = mutableListOf<ActivePanel>()
        doAnswer { call -> call.getArgument<ActivePanel>(0).also(rows::add) }.`when`(repository).save(any(ActivePanel::class.java))
        val activity = ActivePanelLogService(repository)
        activity.addText("[검색] 사전 변경")
        activity.addText("공개 이력", published = true)
        `when`(repository.findAllByOrderByApNoDesc(PageRequest.of(0, 20))).thenReturn(PageImpl(rows))
        for (actor in listOf(Actor(), Actor(an = 7), Actor(an = 7, roles = listOf("ROOT")), Actor(an = 7, roles = listOf("TRANSLATOR")))) {
            assertEquals(listOf("공개 이력"), activity.getList("public", 0, actor).content.map { it.data1 })
            assertEquals(if (actor.isAdmin) listOf("[검색] 사전 변경", "공개 이력") else listOf("공개 이력"),
                activity.getList("admin", 0, actor).content.map { it.data1 })
        }
    }
}
