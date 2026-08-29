package anissia.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mindrot.jbcrypt.BCrypt
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@DisplayName("PasswordHasher 기존 해시 호환성")
class PasswordHasherTest {

    private val hasher = PasswordHasher(BCryptPasswordEncoder(10))

    @Test
    fun `기존 jbcrypt 로 생성된 해시를 그대로 검증할 수 있다`() {
        val legacySalt = BCrypt.gensalt(10)
        val legacyHash = BCrypt.hashpw("legacy-password", legacySalt)

        assertTrue(legacyHash.startsWith("\$2a\$10\$"), legacyHash)
        assertTrue(hasher.matches("legacy-password", legacyHash))
        assertFalse(hasher.matches("wrong-password", legacyHash))
    }

    @Test
    fun `새 해시는 기존 jbcrypt 로도 검증된다`() {
        val hashed = hasher.hash("both-ways")

        assertTrue(BCrypt.checkpw("both-ways", hashed))
        assertFalse(BCrypt.checkpw("nope", hashed))
    }

    @Test
    fun `기존처럼 salt 를 공유해 만든 해시도 검증된다`() {
        val sharedSalt = BCrypt.gensalt(10)
        val first = BCrypt.hashpw("user-a", sharedSalt)
        val second = BCrypt.hashpw("user-b", sharedSalt)

        assertTrue(hasher.matches("user-a", first))
        assertTrue(hasher.matches("user-b", second))
        assertFalse(hasher.matches("user-b", first))
    }

    @Test
    fun `새로 만든 해시도 2a 10 포맷이다`() {
        val hashed = hasher.hash("some-password")

        assertTrue(hashed.startsWith("\$2a\$10\$"), hashed)
        assertTrue(hasher.matches("some-password", hashed))
        assertFalse(hasher.matches("some-password!", hashed))
    }

    @Test
    fun `같은 암호라도 해시마다 salt 가 달라진다`() {
        val first = hasher.hash("same-password")
        val second = hasher.hash("same-password")

        assertNotEquals(first, second)
        assertTrue(hasher.matches("same-password", first))
        assertTrue(hasher.matches("same-password", second))
    }
}
