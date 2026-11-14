package anissia.wiki

import anissia.security.Actor
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/wiki/requests")
class WikiRequestController(private val service: WikiRequestService) {
    @ModelAttribute
    fun responseHeaders(response: HttpServletResponse) {
        response.setHeader("Cache-Control", "private, no-store")
        response.setHeader("X-Content-Type-Options", "nosniff")
    }

    @GetMapping
    fun list(@RequestParam(defaultValue = "0") page: Int, @RequestParam(required = false) status: WikiRequestStatus?, actor: Actor) =
        ApiResponse.ok(service.list(page, status, actor))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long, actor: Actor) = ApiResponse.ok(service.get(id, actor))

    @PostMapping
    fun submit(@RequestBody request: WikiRequestSubmission, actor: Actor) = ApiResponse.ok(service.submit(request, actor))

    @PostMapping("/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun image(@RequestParam file: MultipartFile, @RequestParam description: String, @RequestParam sourceUrl: String,
        @RequestParam reason: String, actor: Actor, @RequestParam documentKind: String, @RequestParam documentNo: Long) =
        ApiResponse.ok(service.submitImage(file, description, sourceUrl, reason, actor, documentKind, documentNo))

    @GetMapping("/{id}/image")
    fun image(@PathVariable id: Long, actor: Actor): ResponseEntity<FileSystemResource> = ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate()).contentType(MediaType.parseMediaType("image/avif"))
        .header("X-Content-Type-Options", "nosniff")
        .body(FileSystemResource(service.imageFile(id, actor)))

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long, @RequestBody decision: WikiRequestDecision, actor: Actor) =
        ApiResponse.ok(service.approve(id, decision, actor))

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: Long, @RequestBody decision: WikiRequestDecision, actor: Actor) =
        ApiResponse.ok(service.reject(id, decision, actor))

    @PostMapping("/{id}/cancel")
    fun cancel(@PathVariable id: Long, actor: Actor) = ApiResponse.ok(service.cancel(id, actor))
}
