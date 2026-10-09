package anissia.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Texts 유틸")
class TextsTest {

    @Test
    fun `isMail 은 올바른 메일을 통과시키고 잘못된 메일은 거부한다`() {
        assertTrue(Texts.isMail("user@example.com"))
        assertTrue(Texts.isMail("a_b-c@sub.example.co.kr"))
        assertFalse(Texts.isMail("not-a-email"))
        assertFalse(Texts.isMail("a@b"))
        assertFalse(Texts.isMail("@example.com"))
    }

    @Test
    fun `isName 은 2자 이상 16자 이하의 한글 영문 숫자를 허용한다`() {
        assertTrue(Texts.isName("홍길동"))
        assertTrue(Texts.isName("user1"))
        assertFalse(Texts.isName("a"))
        assertFalse(Texts.isName("x".repeat(17)))
        assertFalse(Texts.isName("hello world"))
    }

    @Test
    fun `isWebSite 는 http 또는 https 시작을 허용한다`() {
        assertTrue(Texts.isWebSite("http://a.com"))
        assertTrue(Texts.isWebSite("https://a.com"))
        assertFalse(Texts.isWebSite("ftp://a.com"))
        assertFalse(Texts.isWebSite("a.com"))
    }

    @Test
    fun `isWebSite 는 allowEmpty 가 true 이면 빈 문자열을 허용한다`() {
        assertTrue(Texts.isWebSite("", true))
        assertFalse(Texts.isWebSite("", false))
    }

    @Test
    fun `isAnimeDate 는 빈 문자열, 정상날짜, 99 패턴을 허용한다`() {
        assertTrue(Texts.isAnimeDate(""))
        assertTrue(Texts.isAnimeDate("2024-01-15"))
        assertTrue(Texts.isAnimeDate("2024-01-99"))
        assertTrue(Texts.isAnimeDate("2024-99-99"))
    }

    @Test
    fun `isAnimeDate 는 잘못된 형식을 거부한다`() {
        assertFalse(Texts.isAnimeDate("2024/01/15"))
        assertFalse(Texts.isAnimeDate("2024-1-1"))
        assertFalse(Texts.isAnimeDate("abcd-ef-gh"))
        assertFalse(Texts.isAnimeDate("2024-13-01"))
    }

    @Test
    fun `encodeBase64Url 와 decodeBase64Url 는 서로 역연산이다`() {
        val origin = "안녕하세요 hello!?"
        assertEquals(origin, Texts.decodeBase64Url(Texts.encodeBase64Url(origin)))
    }

    @Test
    fun `sameElements 는 길이가 같고 정렬 후 같은 원소를 가지면 true 이다`() {
        assertTrue(Texts.sameElements(listOf<Int>(), listOf<Int>()))
        assertTrue(Texts.sameElements(listOf(1, 2, 3), listOf(3, 2, 1)))
        assertTrue(Texts.sameElements(listOf("a", "b"), listOf("b", "a")))
    }

    @Test
    fun `sameElements 는 길이가 다르거나 원소가 다르면 false 이다`() {
        assertFalse(Texts.sameElements(listOf(1, 2), listOf(1, 2, 3)))
        assertFalse(Texts.sameElements(listOf(1, 2, 3), listOf(1, 2, 4)))
    }

    @Test
    fun `absoluteToken 은 번호와 토큰으로 분리된다`() {
        assertEquals(12L, "12-abcdef".tokenNumber)
        assertEquals("abcdef", "12-abcdef".tokenValue)
        assertEquals(0L, "".tokenNumber)
        assertEquals("", "".tokenValue)
    }

    @Test
    fun `isBlankHtml 은 글자나 매체가 없는 HTML 을 빈 값으로 본다`() {
        assertTrue(Texts.isBlankHtml(""))
        assertTrue(Texts.isBlankHtml("<p></p>"))
        assertTrue(Texts.isBlankHtml("<p><br></p><p>&nbsp; \u200B</p>"))
        assertFalse(Texts.isBlankHtml("<p>a</p>"))
        assertFalse(Texts.isBlankHtml("<p><img src=\"x.png\"></p>"))
        assertFalse(Texts.isBlankHtml("<hr>"))
    }
}
