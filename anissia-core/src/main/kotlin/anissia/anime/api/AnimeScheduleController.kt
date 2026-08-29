package anissia.anime.api

import anissia.anime.api.dto.AnimeScheduleItem
import anissia.anime.service.AnimeScheduleService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/anime")
class AnimeScheduleController(
    private val animeScheduleService: AnimeScheduleService,
) {
    @GetMapping("/schedule/{week:[0-8]}")
    fun getSchedule(
        @PathVariable week: String,
        @RequestParam(defaultValue = "true") useCache: Boolean,
        @RequestHeader(HttpHeaders.USER_AGENT, required = false) userAgent: String?,
        actor: Actor,
    ): ApiResponse<List<AnimeScheduleItem>> =
        ApiResponse.ok(animeScheduleService.get(week, useCache, actor, userAgent))

    @GetMapping(
        "/schedule/svg/{width:\\d{3}}/{color:[a-f\\d]{36}}",
        produces = ["image/svg+xml;charset=utf-8"],
    )
    fun getScheduleSvg(
        @PathVariable width: String,
        @PathVariable color: String,
        @RequestHeader(HttpHeaders.USER_AGENT, required = false) userAgent: String?,
        actor: Actor,
    ): String = animeScheduleService.getSvg(width, color, actor, userAgent)
}
