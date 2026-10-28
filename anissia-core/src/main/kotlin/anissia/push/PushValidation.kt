package anissia.push

import anissia.support.badRequestUnless
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** 푸시 서비스로만 요청한다. 사용자 URL로 일반 HTTP 클라이언트를 만들지 않는다. */
object PushValidation {
    fun endpoint(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull()
        badRequestUnless(value.length in 1..2048 && uri != null) { "알림 구독 주소가 올바르지 않습니다." }
        val parsed = uri!!
        val host = parsed.host?.lowercase() ?: ""
        val allowed = host == "fcm.googleapis.com" ||
            host == "updates.push.services.mozilla.com" ||
            host.endsWith(".push.services.mozilla.com") ||
            host == "web.push.apple.com" || host.endsWith(".push.apple.com") ||
            host == "wns.windows.com" || host.endsWith(".wns.windows.com") ||
            host == "notify.windows.com" || host.endsWith(".notify.windows.com")
        badRequestUnless(
            parsed.scheme == "https" && allowed && parsed.userInfo == null &&
                parsed.port in listOf(-1, 443) && parsed.fragment == null && !parsed.rawPath.isNullOrBlank(),
        ) { "지원하는 브라우저의 HTTPS 알림 구독 주소가 필요합니다." }
        return parsed
    }

    fun key(value: String, size: Int): ByteArray {
        val bytes = if (value.length <= 128) runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull() else null
        badRequestUnless(bytes?.size == size) { "알림 구독 키가 올바르지 않습니다." }
        return bytes!!
    }

    fun clientId(value: String) {
        badRequestUnless(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
            "알림 기기 식별자가 올바르지 않습니다."
        }
    }

    fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
