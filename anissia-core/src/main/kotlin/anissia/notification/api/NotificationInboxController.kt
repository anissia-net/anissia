package anissia.notification.api

import anissia.notification.api.dto.NotificationInbox
import anissia.notification.service.NotificationInboxService
import anissia.security.Actor
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notification/inbox")
class NotificationInboxController(private val service: NotificationInboxService) {
    @ModelAttribute
    fun noStore(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store")
    }

    @GetMapping
    fun getInbox(actor: Actor): ApiResponse<NotificationInbox> = ApiResponse.ok(service.getInbox(actor))

    @PutMapping("/{id}/read")
    fun markRead(@PathVariable id: Long, actor: Actor): ApiResponse<Unit> = service.markRead(id, actor)

    @PutMapping("/read")
    fun markAllRead(actor: Actor): ApiResponse<Unit> = service.markAllRead(actor)
}
