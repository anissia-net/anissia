package anissia.push

import anissia.security.Actor
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class PushKeys(val p256dh: String = "", val auth: String = "")
class PushSubscribeRequest(
    val endpoint: String = "",
    val keys: PushKeys = PushKeys(),
    val expirationTime: Long? = null,
    val clientId: String = "",
)
class PushEndpointRequest(val endpoint: String = "", val clientId: String = "")
data class PushConfigItem(val enabled: Boolean, val publicKey: String)
data class PushStatusItem(val subscribed: Boolean)

@RestController
@RequestMapping("/push")
class PushController(private val service: PushSubscriptionService, private val sender: PushSender) {
    @ModelAttribute
    fun noStore(response: HttpServletResponse) {
        response.setHeader("Cache-Control", "no-store")
    }

    @GetMapping("/config")
    fun config(): ApiResponse<PushConfigItem> = ApiResponse.ok(PushConfigItem(sender.enabled, sender.publicKey))

    @PostMapping("/subscription")
    fun subscribe(@RequestBody request: PushSubscribeRequest, actor: Actor): ApiResponse<PushStatusItem> =
        ApiResponse.ok(service.subscribe(request, actor))

    // endpoint는 로그와 URL에 남기지 않도록 조회도 요청 본문을 사용한다.
    @PostMapping("/subscription/status")
    fun status(@RequestBody request: PushEndpointRequest, actor: Actor): ApiResponse<PushStatusItem> =
        ApiResponse.ok(service.status(request, actor))

    @DeleteMapping("/subscription")
    fun unsubscribe(@RequestBody request: PushEndpointRequest, actor: Actor): ApiResponse<Unit> {
        service.unsubscribe(request, actor)
        return ApiResponse.ok()
    }
}
