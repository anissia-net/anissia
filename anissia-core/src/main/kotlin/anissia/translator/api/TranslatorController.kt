package anissia.translator.api

import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.translator.api.dto.AddApplyRequest
import anissia.translator.api.dto.NewApplyPollRequest
import anissia.translator.api.dto.TranslatorApplyItem
import anissia.translator.service.TranslatorApplyService
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/translator")
class TranslatorController(
    private val translatorApplyService: TranslatorApplyService,
) {
    @GetMapping("/apply/list/{page:\\d+}")
    fun getApplyList(@PathVariable page: Int): ApiResponse<Page<TranslatorApplyItem>> =
        ApiResponse.ok(translatorApplyService.getList(page))

    @GetMapping("/apply/{applyNo:\\d+}")
    fun getApply(@PathVariable applyNo: Long): ApiResponse<TranslatorApplyItem> =
        ApiResponse.ok(translatorApplyService.get(applyNo))

    @GetMapping("/apply/count")
    fun getNewTranslatorApplyCount(): ApiResponse<Int> =
        ApiResponse.ok(translatorApplyService.getApplyingCount())

    @PostMapping("/apply")
    fun newApply(@RequestBody request: AddApplyRequest, actor: Actor): ApiResponse<Long> =
        translatorApplyService.add(request, actor)

    @PostMapping("/apply/{applyNo:\\d+}/poll")
    fun newApplyPoll(
        @PathVariable applyNo: Long,
        @RequestBody request: NewApplyPollRequest,
        actor: Actor,
    ): ApiResponse<Unit> = translatorApplyService.addPoll(applyNo, request, actor)
}
