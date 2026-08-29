package anissia.activepanel.service

import anissia.activepanel.api.dto.ActivePanelCommandRequest
import anissia.activepanel.api.dto.ActivePanelItem
import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.security.Actor
import anissia.support.badRequestUnless
import anissia.support.filterContent
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActivePanelLogService(
    private val activePanelRepository: ActivePanelRepository,
) {
    @Transactional(readOnly = true)
    fun getList(mode: String, page: Int, actor: Actor): Page<ActivePanelItem> {
        badRequestUnless(mode in ALLOWED_MODES) { "지원하지 않는 mode 입니다." }
        badRequestUnless(page >= 0) { "잘못된 page 입니다." }

        return activePanelRepository.findAllByOrderByApNoDesc(PageRequest.of(page, 20))
            .let { if (mode == "admin" && actor.isAdmin) it else it.filterContent { row -> row.published } }
            .map { ActivePanelItem(it) }
    }

    @Transactional
    fun addText(text: String, published: Boolean = false, actor: Actor? = null) {
        activePanelRepository.save(
            ActivePanel(
                published = published,
                an = actor?.an ?: 0,
                code = "TEXT",
                data1 = text,
            ),
        )
    }

    @Transactional
    fun addNotice(request: ActivePanelCommandRequest, actor: Actor) {
        activePanelRepository.save(
            ActivePanel(
                published = request.published,
                an = actor.an,
                code = "TEXT",
                data1 = "《공지》 ${actor.name} : ${request.text}",
            ),
        )
    }

    companion object {
        private val ALLOWED_MODES = listOf("public", "admin")
    }
}
