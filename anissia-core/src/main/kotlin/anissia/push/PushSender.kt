package anissia.push

import anissia.support.Json
import anissia.support.logger
import nl.martijndwars.webpush.AbstractPushService
import nl.martijndwars.webpush.Encoding
import nl.martijndwars.webpush.Notification
import nl.martijndwars.webpush.Utils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Security
import java.time.Duration

@Component
class PushSender(private val properties: PushProperties) {
    private val log = logger<PushSender>()
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private val encoder: Encoder? = createEncoder()
    val enabled: Boolean get() = encoder != null
    val publicKey: String get() = if (enabled) properties.vapidPublicKey else ""

    private fun createEncoder(): Encoder? {
        if (properties.vapidPublicKey.isBlank() || properties.vapidPrivateKey.isBlank() || properties.vapidSubject.isBlank()) {
            return null
        }
        return runCatching {
            val subject = URI(properties.vapidSubject)
            require(subject.scheme == "mailto" || (subject.scheme == "https" && subject.host != null))
            if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
            Encoder(properties).also { require(Utils.verifyKeyPair(it.privateKey, it.publicKey)) }
        }.getOrElse {
            // 설정값이나 암호화 예외 메시지에는 개인키가 포함될 수 있다.
            log.warn("Web Push 설정이 올바르지 않아 알림 발송을 비활성화했습니다.")
            null
        }
    }

    fun send(delivery: ClaimedPushDelivery): Int {
        return client.send(prepareRequest(delivery), HttpResponse.BodyHandlers.discarding()).statusCode()
    }

    internal fun prepareRequest(delivery: ClaimedPushDelivery): HttpRequest {
        val service = checkNotNull(encoder)
        PushValidation.endpoint(delivery.endpoint)
        val episodeLabel = if (delivery.episode == "0") "단편" else "${delivery.episode}화"
        val payload = Json.write(
            mapOf(
                "clientId" to delivery.clientId,
                "title" to "${delivery.subject} 자막 업데이트",
                "body" to "${delivery.translatorName} · $episodeLabel 자막이 등록되었습니다.",
                "url" to "/anime/${delivery.animeNo}",
                "tag" to "caption-${delivery.eventKey}",
            ),
        ).toByteArray(Charsets.UTF_8)
        val encrypted = service.prepare(
            Notification(delivery.endpoint, delivery.p256dh, delivery.auth, payload, 86400),
        )
        val request = HttpRequest.newBuilder(PushValidation.endpoint(encrypted.url))
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofByteArray(encrypted.body))
        encrypted.headers.forEach { (name, value) -> request.header(name, value) }
        return request.build()
    }

    private class Encoder(properties: PushProperties) : AbstractPushService<Encoder>(
        properties.vapidPublicKey, properties.vapidPrivateKey, properties.vapidSubject,
    ) {
        fun prepare(notification: Notification): nl.martijndwars.webpush.HttpRequest =
            prepareRequest(notification, Encoding.AES128GCM)
    }
}
