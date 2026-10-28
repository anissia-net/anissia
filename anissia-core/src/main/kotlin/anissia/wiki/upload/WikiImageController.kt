package anissia.wiki.upload

import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.core.io.FileSystemResource
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.time.Duration

@RestController
@RequestMapping("/wiki")
class WikiImageController(private val images: WikiImageService) {
    @PostMapping("/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestParam file: MultipartFile,
        @RequestParam(defaultValue = "") description: String,
        @RequestParam(defaultValue = "") sourceUrl: String,
        actor: Actor,
    ): ResponseEntity<ApiResponse<WikiImageDto>> = ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(images.upload(file, description, sourceUrl, actor)))

    @GetMapping("/images/{id}")
    fun detail(@PathVariable id: String, actor: Actor): ResponseEntity<ApiResponse<WikiImageDto>> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(images.get(id, actor)))

    @GetMapping("/images")
    fun list(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "") q: String,
             @RequestParam(defaultValue = "false") deleted: Boolean, actor: Actor): ResponseEntity<ApiResponse<WikiImagePage>> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(images.list(page, q, deleted, actor)))

    @PutMapping("/images/{id}")
    fun edit(@PathVariable id: String, @RequestBody request: WikiImageEditRequest, actor: Actor) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(images.edit(id, request, actor)))

    @GetMapping("/images/{id}/usage")
    fun usage(@PathVariable id: String, @RequestParam(defaultValue = "0") page: Int, actor: Actor) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(images.usage(id, page, actor)))

    @DeleteMapping("/images/{id}")
    fun delete(@PathVariable id: String, @RequestBody request: WikiImageOperationRequest, actor: Actor) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(images.delete(id, request, actor)))

    @PostMapping("/images/{id}/restore")
    fun restore(@PathVariable id: String, @RequestBody request: WikiImageOperationRequest, actor: Actor) =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(images.restore(id, request, actor)))

    @GetMapping("/files/wiki/{id}.thumb.avif")
    fun thumbnail(@PathVariable id: String): ResponseEntity<FileSystemResource> = fileResponse(images.readyThumbnailFile(id))

    @GetMapping("/files/wiki/{id}.avif")
    fun file(@PathVariable id: String): ResponseEntity<FileSystemResource> = fileResponse(images.readyFile(id))

    private fun fileResponse(path: java.nio.file.Path): ResponseEntity<FileSystemResource> {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/avif"))
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .header("X-Content-Type-Options", "nosniff")
            .body(FileSystemResource(path))
    }
}
