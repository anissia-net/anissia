package anissia.security

import me.saro.dat.dat.DatCmsManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Socket

@DisplayName("DatTokenService 토큰 발급/파싱")
class DatTokenServiceTest {

    @Test
    fun `발급한 토큰은 동일한 actor 로 복원된다`() {
        val actor = Actor(
            an = 1234,
            name = "홍길동",
            email = "user@example.com",
            roles = listOf("ROOT", "TRANSLATOR"),
            ip = "10.0.0.1",
        )

        val parsed = service.parse(service.issue(actor), "10.0.0.2")

        assertEquals(actor.an, parsed.an)
        assertEquals(actor.name, parsed.name)
        assertEquals(actor.email, parsed.email)
        assertEquals(actor.roles, parsed.roles)
        assertEquals("10.0.0.2", parsed.ip)
        assertTrue(parsed.isRoot)
        assertTrue(parsed.isAdmin)
    }

    @Test
    fun `권한이 없는 계정도 왕복 변환된다`() {
        val parsed = service.parse(
            service.issue(Actor(an = 7, name = "user1", email = "a@b.com", roles = listOf())),
            IP,
        )

        assertEquals(7L, parsed.an)
        assertTrue(parsed.isLogin)
        assertFalse(parsed.isAdmin)
        assertTrue(parsed.roles.isEmpty())
    }

    @Test
    fun `토큰이 없으면 익명 actor 가 된다`() {
        assertFalse(service.parse(null, IP).isLogin)
        assertFalse(service.parse("", IP).isLogin)
        assertFalse(service.parse("   ", IP).isLogin)
    }

    @Test
    fun `위조된 토큰은 익명 actor 가 된다`() {
        assertFalse(service.parse("not-a-dat", IP).isLogin)
        assertFalse(service.parse("a.b.c.d.e.f", IP).isLogin)
    }

    @Test
    fun `서명이 훼손된 토큰은 익명 actor 가 된다`() {
        val issued = service.issue(Actor(an = 1, name = "n", email = "e@f.com"))
        val tampered = issued.dropLast(4) + "AAAA"

        assertFalse(service.parse(tampered, IP).isLogin)
    }

    companion object {
        private const val IP = "127.0.0.1"
        private const val CMS_URI = "http://localhost:8088"

        private lateinit var service: DatTokenService

        @BeforeAll
        @JvmStatic
        fun setUp() {
            assumeTrue(isCmsReachable(), "DAT CMS($CMS_URI) 가 없으면 건너뛴다")
            service = DatTokenService(DatCmsManager.builder().uri(CMS_URI).intervalOff().build())
        }

        private fun isCmsReachable(): Boolean =
            try {
                Socket().use {
                    it.connect(InetSocketAddress("localhost", 8088), 500)
                    true
                }
            } catch (_: Exception) {
                false
            }
    }
}
