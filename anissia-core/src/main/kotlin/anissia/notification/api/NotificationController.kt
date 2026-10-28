package anissia.notification.api

import anissia.notification.api.dto.EditNotificationRequest
import anissia.notification.api.dto.NotificationItem
import anissia.notification.service.NotificationService
import anissia.security.Actor
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
// 이전 클라이언트도 기존 저장 데이터를 계속 사용할 수 있도록 이전 API 경로를 유지한다.
@RequestMapping("/notification", "/favorite")
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping
    fun getList(actor: Actor, response: HttpServletResponse): ApiResponse<List<NotificationItem>> {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        return ApiResponse.ok(notificationService.getList(actor))
    }

    @PutMapping("/{animeNo}")
    fun put(
        @PathVariable animeNo: Long,
        @RequestBody request: EditNotificationRequest,
        actor: Actor,
    ): ApiResponse<Unit> = notificationService.put(animeNo, request, actor)

    @DeleteMapping("/{animeNo}")
    fun delete(@PathVariable animeNo: Long, actor: Actor): ApiResponse<Unit> = notificationService.delete(animeNo, actor)
}
