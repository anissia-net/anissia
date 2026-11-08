package anissia.activepanel

import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ActivePanelLogServiceTest {
    private val repository = mock(ActivePanelRepository::class.java)
    private val service = ActivePanelLogService(repository)
    private val adminActors = listOf(
        Actor(an = 1, roles = listOf("ROOT")),
        Actor(an = 2, roles = listOf("TRANSLATOR")),
    )
    private val publicActors = listOf(
        Actor(an = 3),
        Actor.anonymous("127.0.0.1"),
        Actor(roles = listOf("ROOT", "TRANSLATOR")),
    )

    private fun prepareHistory() {
        val rows = listOf(
            ActivePanel(apNo = 2, published = false, code = "TEXT", data1 = "검색 사전 운영 적용"),
            ActivePanel(apNo = 1, published = true, code = "TEXT", data1 = "공개 자막 등록"),
        )
        val page = PageRequest.of(0, 20)
        `when`(repository.findAllByOrderByApNoDesc(page)).thenReturn(PageImpl(rows, page, rows.size.toLong()))
    }

    @Test
    fun `ROOT와 자막 제작자는 관리자 모드에서 비공개 검색 이력을 조회한다`() {
        prepareHistory()
        for (actor in adminActors) {
            assertEquals(listOf(2L, 1L), service.getList("admin", 0, actor).content.map { it.apNo })
        }
    }

    @Test
    fun `일반회원과 익명은 관리자 모드를 지정해도 비공개 검색 이력을 볼 수 없다`() {
        prepareHistory()
        for (actor in publicActors) {
            assertEquals(listOf(1L), service.getList("admin", 0, actor).content.map { it.apNo })
        }
    }

    @Test
    fun `공개 모드에서는 관리자도 비공개 검색 이력을 볼 수 없다`() {
        prepareHistory()
        for (actor in adminActors + publicActors) {
            assertEquals(listOf(1L), service.getList("public", 0, actor).content.map { it.apNo })
        }
    }
}
