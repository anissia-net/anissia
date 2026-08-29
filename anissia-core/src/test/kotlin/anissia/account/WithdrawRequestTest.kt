package anissia.account

import anissia.account.api.dto.WithdrawRequest
import anissia.support.BadRequestException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WithdrawRequest.validate")
class WithdrawRequestTest {

    @Test
    fun `암호가 비면 검증 실패한다`() {
        val ex = assertThrows(BadRequestException::class.java) { WithdrawRequest().validate() }
        assertEquals("암호를 입력해 주세요.", ex.message)
    }

    @Test
    fun `공백만 있어도 검증 실패한다`() {
        assertThrows(BadRequestException::class.java) { WithdrawRequest("   ").validate() }
    }

    @Test
    fun `암호가 있으면 통과한다`() {
        assertDoesNotThrow { WithdrawRequest("testtest").validate() }
    }
}
