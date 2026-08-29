package anissia.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExceptionsTest {

    @Test
    fun `FailException 기본 메시지는 알수없는 오류이다`() {
        assertEquals("알수없는 오류입니다.", FailException().message)
    }

    @Test
    fun `FailException 메시지를 직접 지정할 수 있다`() {
        assertEquals("커스텀 오류", FailException("커스텀 오류").message)
    }

    @Test
    fun `ErrorException 메시지를 직접 지정할 수 있다`() {
        assertEquals("심각 오류", ErrorException("심각 오류").message)
    }

    @Test
    fun `BadRequestException 은 FailException 으로 처리된다`() {
        val ex = assertThrows(FailException::class.java) { badRequest("잘못된 요청") }
        assertEquals("잘못된 요청", ex.message)
        assertTrue(ex is BadRequestException)
    }

    @Test
    fun `badRequestIf 는 조건이 true 일 때만 예외를 던진다`() {
        badRequestIf(false) { "err" }
        assertThrows(BadRequestException::class.java) { badRequestIf(true) { "err" } }
    }

    @Test
    fun `badRequestUnless 는 조건이 false 일 때만 예외를 던진다`() {
        badRequestUnless(true) { "err" }
        assertThrows(BadRequestException::class.java) { badRequestUnless(false) { "err" } }
    }

    @Test
    fun `badRequestOnFailure 는 내부 람다가 예외를 던지면 검증 실패로 변환한다`() {
        val ex = assertThrows(BadRequestException::class.java) {
            badRequestOnFailure("변환됨") { error("inside") }
        }
        assertEquals("변환됨", ex.message)
    }

    @Test
    fun `badRequestOnFailure 는 성공하면 값을 그대로 반환한다`() {
        assertEquals(3, badRequestOnFailure("변환됨") { 3 })
    }

    @Test
    fun `failIf 는 조건이 true 일 때만 예외를 던진다`() {
        failIf(false) { "err" }
        assertThrows(FailException::class.java) { failIf(true) { "err" } }
    }
}
