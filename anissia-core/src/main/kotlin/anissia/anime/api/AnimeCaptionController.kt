package anissia.anime.api

import anissia.anime.api.dto.CaptionItem
import anissia.anime.api.dto.CaptionRecentItem
import anissia.anime.api.dto.EditCaptionRequest
import anissia.anime.api.dto.MyCaptionItem
import anissia.anime.service.AnimeCaptionService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/anime")
class AnimeCaptionController(
    private val animeCaptionService: AnimeCaptionService,
) {
    @GetMapping("/caption/animeNo/{animeNo:\\d+}")
    fun getCaptionListByAnimeNo(@PathVariable animeNo: Long, actor: Actor): ApiResponse<List<CaptionItem>> =
        ApiResponse.ok(animeCaptionService.getListByAnimeNo(animeNo, actor))

    @GetMapping("/caption/myList/{active}/{page}")
    fun getMyCaptionList(
        @PathVariable active: Int,
        @PathVariable page: Int,
        actor: Actor,
    ): ApiResponse<Page<MyCaptionItem>> = ApiResponse.ok(animeCaptionService.getMyList(active, page, actor))

    @GetMapping("/caption/recent")
    fun getCaptionRecent(): ApiResponse<List<CaptionRecentItem>> =
        ApiResponse.ok(animeCaptionService.getRecent(-1).content)

    @GetMapping("/caption/recent/{page:\\d+}")
    fun getCaptionRecent(@PathVariable page: Int): ApiResponse<Page<CaptionRecentItem>> =
        ApiResponse.ok(animeCaptionService.getRecent(page))

    @DeleteMapping("/caption/{animeNo}")
    fun deleteCaption(@PathVariable animeNo: Long, actor: Actor): ApiResponse<Unit> =
        animeCaptionService.delete(animeNo, actor)

    @PostMapping("/caption/{animeNo}")
    fun newCaption(@PathVariable animeNo: Long, actor: Actor): ApiResponse<Unit> =
        animeCaptionService.add(animeNo, actor)

    @PutMapping("/caption/{animeNo}")
    fun editCaption(
        @PathVariable animeNo: Long,
        @RequestBody request: EditCaptionRequest,
        actor: Actor,
    ): ApiResponse<Unit> = animeCaptionService.edit(animeNo, request, actor)
}
