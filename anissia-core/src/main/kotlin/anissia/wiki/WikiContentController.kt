package anissia.wiki

import anissia.security.Actor
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/wiki")
class WikiContentController(private val service: WikiContentService) {
    @ModelAttribute
    fun responseHeaders(response: HttpServletResponse) {
        response.setHeader("Cache-Control", "no-store")
    }

    @GetMapping("/anime/{animeNo}")
    fun anime(@PathVariable animeNo: Long) = ApiResponse.ok(service.animeDocument(animeNo))

    @GetMapping("/anime/{animeNo}/images")
    fun animeImages(@PathVariable animeNo: Long, actor: Actor) = ApiResponse.ok(service.animeImages(animeNo, actor))

    @PutMapping("/anime/{animeNo}/thumbnail")
    fun selectAnimeThumbnail(@PathVariable animeNo: Long, @RequestBody request: WikiThumbnailRequest, actor: Actor) =
        ApiResponse.ok(service.selectAnimeThumbnail(animeNo, request, actor))

    @PutMapping("/anime/{animeNo}")
    fun editAnime(@PathVariable animeNo: Long, @RequestBody request: WikiEditRequest, actor: Actor) = ApiResponse.ok(service.editAnime(animeNo, request, actor))

    @GetMapping("/templates")
    fun templates(@RequestParam(defaultValue = "") q: String, @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "false") deleted: Boolean, actor: Actor) = ApiResponse.ok(service.templateList(q, page, deleted, actor))

    @GetMapping("/recent")
    fun recent(@RequestParam(defaultValue = "0") page: Int, actor: Actor) = ApiResponse.ok(service.recent(page, actor))

    @PostMapping("/templates")
    fun createTemplate(@RequestBody request: WikiEditRequest, actor: Actor) = ApiResponse.ok(service.createTemplate(request, actor))

    @GetMapping("/templates/{templateNo}")
    fun template(@PathVariable templateNo: Long, actor: Actor) = ApiResponse.ok(service.templateDocument(templateNo, actor))

    @GetMapping("/templates/{templateNo}/usage")
    fun usage(@PathVariable templateNo: Long, @RequestParam(defaultValue = "0") page: Int, actor: Actor) =
        ApiResponse.ok(service.templateUsage(templateNo, page, actor))

    @PostMapping("/templates/{templateNo}/recover")
    fun recover(@PathVariable templateNo: Long, @RequestBody request: WikiRestoreRequest, actor: Actor) =
        ApiResponse.ok(service.recoverTemplate(templateNo, request.baseRevision, actor))

    @PutMapping("/templates/{templateNo}")
    fun editTemplate(@PathVariable templateNo: Long, @RequestBody request: WikiEditRequest, actor: Actor) = ApiResponse.ok(service.editTemplate(templateNo, request, actor))

    @DeleteMapping("/templates/{templateNo}")
    fun deleteTemplate(@PathVariable templateNo: Long, @RequestBody request: WikiRestoreRequest, actor: Actor): ApiResponse<Unit> {
        service.deleteTemplate(templateNo, request.baseRevision, actor)
        return ApiResponse.ok()
    }

    @GetMapping("/{collection:anime|templates}/{id}/history")
    fun history(@PathVariable collection: String, @PathVariable id: Long, @RequestParam(defaultValue = "0") page: Int, actor: Actor) = ApiResponse.ok(service.history(kind(collection), id, page, actor))

    @GetMapping("/{collection:anime|templates}/{id}/history/{revision}")
    fun revision(@PathVariable collection: String, @PathVariable id: Long, @PathVariable revision: Long, actor: Actor) = ApiResponse.ok(service.revision(kind(collection), id, revision, actor))

    @PostMapping("/{collection:anime|templates}/{id}/restore/{revision}")
    fun restore(@PathVariable collection: String, @PathVariable id: Long, @PathVariable revision: Long, @RequestBody request: WikiRestoreRequest, actor: Actor) =
        ApiResponse.ok(service.restore(kind(collection), id, revision, request, actor))

    private fun kind(collection: String) = if (collection == "templates") "template" else "anime"
}
