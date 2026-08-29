package anissia.activepanel.api

import anissia.activepanel.api.dto.ActivePanelCommandRequest
import anissia.activepanel.api.dto.ActivePanelItem
import anissia.activepanel.service.ActivePanelCommandService
import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/active-panel")
class ActivePanelController(
    private val activePanelLogService: ActivePanelLogService,
    private val activePanelCommandService: ActivePanelCommandService,
) {
    @GetMapping("/list/{page:[\\d]+}")
    fun getList(
        @PathVariable page: Int,
        @RequestParam(defaultValue = "") mode: String,
        actor: Actor,
    ): ApiResponse<Page<ActivePanelItem>> = ApiResponse.ok(activePanelLogService.getList(mode, page, actor))

    @PostMapping("/command")
    fun doCommand(@RequestBody request: ActivePanelCommandRequest, actor: Actor): ApiResponse<Unit> =
        activePanelCommandService.doCommand(request, actor)
}
