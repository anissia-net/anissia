package anissia.support

import anissia.anime.api.dto.AnimeItem
import anissia.anime.api.dto.AnimeRankItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private data class Sample(
    val name: String = "default",
    val count: Int = 7,
)

@DisplayName("Json 매퍼 호환 설정")
class JsonTest {

    @Test
    fun `write 는 객체를 JSON 으로 직렬화한다`() {
        val json = Json.write(mapOf("a" to 1, "b" to "x"))
        assertTrue(json.contains("\"a\":1"))
        assertTrue(json.contains("\"b\":\"x\""))
    }

    @Test
    fun `필드가 없으면 코틀린 기본값을 사용한다`() {
        val sample = Json.read<Sample>("{}")
        assertEquals("default", sample.name)
        assertEquals(7, sample.count)
    }

    @Test
    fun `명시적 null 은 예외 없이 처리된다`() {
        val sample = Json.read<Sample>("""{"name":null,"count":null}""")
        assertEquals("default", sample.name)
        assertEquals(0, sample.count)
    }

    @Test
    fun `알 수 없는 필드는 무시된다`() {
        val sample = Json.read<Sample>("""{"name":"n","unknown":1}""")
        assertEquals("n", sample.name)
    }

    @Test
    fun `AnimeItem 은 저장된 payload 로 왕복 변환된다`() {
        val stored = Json.write(
            AnimeItem(
                animeNo = 3,
                status = "ON",
                week = "1",
                time = "12:30",
                subject = "제목",
                captionCount = 2,
                genres = "액션",
            ),
        )
        val parsed = Json.read<AnimeItem>(stored)
        assertEquals(3L, parsed.animeNo)
        assertEquals("ON", parsed.status)
        assertEquals("제목", parsed.subject)
        assertEquals(2, parsed.captionCount)
        assertTrue(parsed.captions.isEmpty())
    }

    @Test
    fun `AnimeRankItem 직렬화는 exist 와 diff 필드를 유지한다`() {
        val json = Json.write(listOf(AnimeRankItem(animeNo = 1, subject = "s", hit = 5)))
        assertTrue(json.contains(""""exist":true"""))
        assertTrue(json.contains(""""diff":null"""))
        assertTrue(json.contains(""""hit":5"""))
    }
}
