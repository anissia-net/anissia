package anissia.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RecordCodec dat payload 인코딩")
class RecordCodecTest {

    private val codec = RecordCodec(version = "2", recordCount = 3)

    @Test
    fun `write 후 read 하면 원본 레코드가 복원된다`() {
        val encoded = codec.write("a@b.com", "홍길동", "ROOT,TRANSLATOR")
        assertEquals(listOf("a@b.com", "홍길동", "ROOT,TRANSLATOR"), codec.read(encoded))
    }

    @Test
    fun `빈 롤도 레코드 수를 유지한다`() {
        val record = codec.read(codec.write("a@b.com", "홍길동", ""))
        assertEquals(3, record.size)
        assertEquals("", record[2])
    }

    @Test
    fun `버전이 다르면 빈 목록을 반환한다`() {
        val other = RecordCodec(version = "1", recordCount = 3)
        assertTrue(codec.read(other.write("a", "b", "c")).isEmpty())
    }

    @Test
    fun `레코드 수가 다르면 빈 목록을 반환한다`() {
        assertTrue(codec.read(codec.write("a", "b")).isEmpty())
    }

    @Test
    fun `version 이 비면 생성할 수 없다`() {
        assertThrows(IllegalArgumentException::class.java) { RecordCodec("", 1) }
    }

    @Test
    fun `recordCount 는 1 이상이어야 한다`() {
        assertThrows(IllegalArgumentException::class.java) { RecordCodec("1", 0) }
    }
}
