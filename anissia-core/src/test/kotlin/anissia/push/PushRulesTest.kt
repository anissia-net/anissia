package anissia.push

import anissia.support.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushRulesTest {
    @Test
    fun `first public URL and increasing episodes notify`() {
        assertTrue(CaptionPushOutbox.isNewPublication("0", "", "1", "https://example.com/1"))
        assertTrue(CaptionPushOutbox.isNewPublication("9", "https://example.com", "10", "https://example.com"))
        assertTrue(CaptionPushOutbox.isNewPublication("12", "https://example.com", "12.5", "https://example.com"))
    }

    @Test
    fun `URL correction formatting change decrease and hidden URL do not notify`() {
        assertFalse(CaptionPushOutbox.isNewPublication("12", "https://example.com/old", "12.00", "https://example.com/new"))
        assertFalse(CaptionPushOutbox.isNewPublication("12", "https://example.com", "11", "https://example.com"))
        assertFalse(CaptionPushOutbox.isNewPublication("12", "https://example.com", "13", ""))
        assertEquals("12", CaptionPushOutbox.normalizeEpisode("00012.00"))
        assertEquals("12.5", CaptionPushOutbox.normalizeEpisode("12.50"))
        assertEquals("0", CaptionPushOutbox.normalizeEpisode("0.00"))
    }

    @Test
    fun `only HTTPS browser push hosts can be requested`() {
        for (endpoint in listOf(
            "https://fcm.googleapis.com/fcm/send/example",
            "https://updates.push.services.mozilla.com/wpush/v2/example",
            "https://web.push.apple.com/example",
            "https://wns2-sg2p.notify.windows.com/w/?token=example",
        )) assertEquals("https", PushValidation.endpoint(endpoint).scheme)

        for (endpoint in listOf(
            "http://fcm.googleapis.com/push",
            "https://127.0.0.1/push",
            "https://169.254.169.254/latest/meta-data/",
            "https://fcm.googleapis.com.attacker.example/push",
            "https://notpush.apple.com/push",
            "https://fcm.googleapis.com@localhost/push",
            "https://fcm.googleapis.com:8443/push",
            "https://fcm.googleapis.com/push#fragment",
        )) assertThrows(BadRequestException::class.java) { PushValidation.endpoint(endpoint) }
    }
}
