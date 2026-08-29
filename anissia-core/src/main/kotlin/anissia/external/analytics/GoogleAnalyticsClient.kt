package anissia.external.analytics

import anissia.support.encodeUrl
import anissia.support.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GoogleAnalyticsClient(
    private val restClient: RestClient,
    @Value("\${google.analytics.id}") private val id: String,
) {
    private val log = logger<GoogleAnalyticsClient>()

    @Async
    fun pageView(path: String, ip: String, userAgent: String) {
        val payload = "v=1&tid=$id&cid=$ip&t=pageview&dp=${path.encodeUrl()}&uip=$ip&ua=${userAgent.encodeUrl()}"
        try {
            restClient.post()
                .uri(COLLECT_URL)
                .contentType(MediaType.TEXT_PLAIN)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("google analytics collect failed: {}", e.message)
        }
    }

    companion object {
        private const val COLLECT_URL = "https://www.google-analytics.com/collect"
    }
}
