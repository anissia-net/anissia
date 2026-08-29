package anissia.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ApiResponse 기본 동작")
class ApiResponseTest {

    @Test
    fun `ok() 는 code=ok 이고 message 와 data 가 null 이다`() {
        val r = ApiResponse.ok()
        assertEquals("ok", r.code)
        assertNull(r.message)
        assertNull(r.data)
    }

    @Test
    fun `fail(msg) 는 code=fail 이고 메시지가 그대로 담긴다`() {
        val r = ApiResponse.fail("nope")
        assertEquals("fail", r.code)
        assertEquals("nope", r.message)
        assertNull(r.data)
    }

    @Test
    fun `error(msg) 는 code=error 이고 메시지가 그대로 담긴다`() {
        val r = ApiResponse.error("boom")
        assertEquals("error", r.code)
        assertEquals("boom", r.message)
        assertNull(r.data)
    }

    @Test
    fun `제네릭 ok 는 데이터를 그대로 보관한다`() {
        val r = ApiResponse.ok<Long>(123L)
        assertEquals("ok", r.code)
        assertEquals(123L, r.data)
        assertNull(r.message)
    }

    @Test
    fun `제네릭 fail 은 메시지와 데이터 모두 보관한다`() {
        val r = ApiResponse.fail<Long>("bad", -1L)
        assertEquals("fail", r.code)
        assertEquals("bad", r.message)
        assertEquals(-1L, r.data)
    }

    @Test
    fun `of 는 임의의 코드 메시지 데이터를 모두 받을 수 있다`() {
        val r = ApiResponse.of("custom", "msg", 42)
        assertEquals("custom", r.code)
        assertEquals("msg", r.message)
        assertEquals(42, r.data)
    }

    @Test
    fun `직렬화시 null 인 message 와 data 는 제외된다`() {
        assertEquals("""{"code":"ok"}""", Json.write(ApiResponse.ok()))
    }

    @Test
    fun `직렬화시 값이 있는 message 와 data 는 포함된다`() {
        val json = Json.write(ApiResponse.of("ok", "", 7L))
        assertTrue(json.contains(""""code":"ok""""))
        assertTrue(json.contains(""""message":""""))
        assertTrue(json.contains(""""data":7"""))
        assertFalse(json.contains("null"))
    }
}
