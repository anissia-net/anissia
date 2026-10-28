package anissia.push

import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class PushSenderTest {
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    private fun keyPair(): KeyPair {
        if (Security.getProvider("BC") == null) Security.addProvider(BouncyCastleProvider())
        return KeyPairGenerator.getInstance("EC", "BC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
    }

    private fun publicKey(pair: KeyPair): String = base64.encodeToString((pair.public as ECPublicKey).q.getEncoded(false))

    private fun privateKey(pair: KeyPair): String {
        val bytes = (pair.private as ECPrivateKey).d.toByteArray()
        return base64.encodeToString(ByteArray(32).also { result ->
            val size = minOf(bytes.size, result.size)
            bytes.copyInto(result, result.size - size, bytes.size - size)
        })
    }

    @Test
    fun `valid VAPID keys produce encrypted authenticated browser push without HTTP client dependencies`() {
        val vapid = keyPair()
        val sender = PushSender(PushProperties(publicKey(vapid), privateKey(vapid), "mailto:local@example.invalid"))
        assertTrue(sender.enabled)
        assertEquals(publicKey(vapid), sender.publicKey)

        val request = sender.prepareRequest(ClaimedPushDelivery(
            id = 1, token = "test", attempts = 1, eventKey = "event", animeNo = 11, episode = "1",
            subject = "로컬 검증", translatorName = "검증용 관리자", subscriptionId = 1,
            endpoint = "https://fcm.googleapis.com/fcm/send/test",
            p256dh = publicKey(keyPair()), auth = base64.encodeToString(ByteArray(16) { it.toByte() }),
            clientId = "00000000-0000-0000-0000-000000000001",
        ))

        assertEquals("POST", request.method())
        assertEquals("aes128gcm", request.headers().firstValue("Content-Encoding").orElseThrow())
        assertEquals("86400", request.headers().firstValue("TTL").orElseThrow())
        assertTrue(request.headers().firstValue("Authorization").orElseThrow().startsWith("vapid "))
        assertTrue(request.bodyPublisher().orElseThrow().contentLength() > 100)
    }

    @Test
    fun `missing or mismatched VAPID keys disable push`() {
        assertFalse(PushSender(PushProperties()).enabled)
        val first = keyPair()
        val second = keyPair()
        val sender = PushSender(PushProperties(publicKey(first), privateKey(second), "mailto:local@example.invalid"))
        assertFalse(sender.enabled)
        assertEquals("", sender.publicKey)
    }
}
