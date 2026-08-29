package anissia.session

import anissia.session.api.dto.TokenLoginRequest
import anissia.session.api.dto.UserLoginRequest
import anissia.support.FailException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("UserLoginRequest.validate")
class UserLoginRequestTest {

    @Test
    fun `이메일 형식이 잘못되면 이메일 메시지로 실패한다`() {
        val ex = assertThrows(FailException::class.java) {
            UserLoginRequest(email = "notmail", password = "12345678").validate()
        }
        assertEquals("아이디는 E-MAIL 형식입니다.", ex.message)
    }

    @Test
    fun `이메일이 너무 짧으면 실패한다`() {
        val ex = assertThrows(FailException::class.java) {
            UserLoginRequest(email = "a@b", password = "12345678").validate()
        }
        assertEquals("아이디는 E-MAIL 형식입니다.", ex.message)
    }

    @Test
    fun `비밀번호가 8자 미만이면 암호 메시지로 실패한다`() {
        val ex = assertThrows(FailException::class.java) {
            UserLoginRequest(email = "user@example.com", password = "1234567").validate()
        }
        assertEquals("암호는 8자리 이상 128자리 이하로 작성해야합니다.", ex.message)
    }

    @Test
    fun `비밀번호가 128자 초과면 실패한다`() {
        val ex = assertThrows(FailException::class.java) {
            UserLoginRequest(email = "user@example.com", password = "x".repeat(129)).validate()
        }
        assertEquals("암호는 8자리 이상 128자리 이하로 작성해야합니다.", ex.message)
    }

    @Test
    fun `정상 입력은 통과한다`() {
        assertDoesNotThrow {
            UserLoginRequest(email = "user@example.com", password = "12345678").validate()
        }
    }

    @Test
    fun `makeLoginToken 기본값은 false 이다`() {
        org.junit.jupiter.api.Assertions.assertEquals(false, UserLoginRequest().makeLoginToken)
    }

    @Test
    fun `TokenLoginRequest 는 절대토큰을 번호와 토큰으로 분리한다`() {
        val request = TokenLoginRequest("42-secrettoken")
        assertEquals(42L, request.tokenNo)
        assertEquals("secrettoken", request.token)
    }
}
