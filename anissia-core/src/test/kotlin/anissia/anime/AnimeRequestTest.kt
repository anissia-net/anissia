package anissia.anime

import anissia.anime.api.dto.EditAnimeRequest
import anissia.anime.api.dto.EditCaptionRequest
import anissia.anime.api.dto.NewAnimeRequest
import anissia.support.BadRequestException
import anissia.support.DateFormats
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("NewAnimeRequest.validate")
class NewAnimeRequestTest {

    private fun valid() = NewAnimeRequest(
        status = "ON",
        week = "1",
        time = "12:30",
        subject = "신작 애니",
        originalSubject = "Original",
        genres = "액션,코미디",
        startDate = "2024-01-01",
        endDate = "2024-12-31",
        website = "https://a.com",
        x = "https://t.com",
    )

    @Test
    fun `정상 입력은 검증을 통과한다`() {
        assertDoesNotThrow { valid().validate() }
    }

    @Test
    fun `time 이 형식과 다르면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            NewAnimeRequest(status = "ON", week = "1", time = "1:2", subject = "s", genres = "액션").validate()
        }
        assertEquals("잘못된 시간입니다.", ex.message)
    }

    @Test
    fun `week 가 0~8 외이면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            NewAnimeRequest(status = "ON", week = "9", time = "12:00", subject = "s", genres = "액션").validate()
        }
        assertEquals("잘못된 요일입니다.", ex.message)
    }

    @Test
    fun `subject 가 공백이면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            NewAnimeRequest(status = "ON", week = "0", time = "12:00", subject = "   ", genres = "액션").validate()
        }
        assertEquals("애니메이션 제목을 입력해주세요.", ex.message)
    }

    @Test
    fun `subject 앞뒤 공백이 있으면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            NewAnimeRequest(status = "ON", week = "0", time = "12:00", subject = " 신작 ", genres = "액션").validate()
        }
        assertEquals("애니메이션 제목에 공백을 제거해 주세요.", ex.message)
    }

    @Test
    fun `존재하지 않는 status 이면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            NewAnimeRequest(status = "NOPE", week = "0", time = "12:00", subject = "s", genres = "액션").validate()
        }
        assertEquals("존재하지 않는 상태입니다.", ex.message)
    }

    @Test
    fun `장르가 3개를 넘으면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            valid().copy(genres = "a,b,c,d").validate()
        }
        assertEquals("장르는 3개까지만 입력가능합니다.", ex.message)
    }

    @Test
    fun `시작일이 종료일보다 미래이면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            valid().copy(startDate = "2024-12-31", endDate = "2024-01-01").validate()
        }
        assertEquals("시작일은 종료일보다 미래일 수 없습니다.", ex.message)
    }

    @Test
    fun `website 가 http 로 시작하지 않으면 검증 실패`() {
        assertThrows(BadRequestException::class.java) { valid().copy(website = "a.com").validate() }
    }
}

@DisplayName("EditAnimeRequest.validate")
class EditAnimeRequestTest {

    private fun valid() = EditAnimeRequest(
        status = "ON",
        week = "1",
        time = "12:30",
        subject = "수정 애니",
        originalSubject = "Original",
        genres = "액션",
        startDate = "2024-01-01",
        endDate = "2024-12-31",
        website = "https://a.com",
        x = "https://t.com",
        note = "메모",
    )

    @Test
    fun `정상 입력은 검증을 통과한다`() {
        assertDoesNotThrow { valid().validate(10) }
    }

    @Test
    fun `animeNo 가 0 이하이면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) { valid().validate(0) }
        assertEquals("animeNo 는 0 이상이어야 합니다.", ex.message)
    }

    @Test
    fun `note 길이가 512 초과이면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            valid().copy(note = "x".repeat(513)).validate(1)
        }
        assertEquals("비고는 512자 이하로 입력해주세요.", ex.message)
    }
}

@DisplayName("EditCaptionRequest.validate")
class EditCaptionRequestTest {

    @Test
    fun `정상 입력은 검증을 통과한다`() {
        assertDoesNotThrow {
            EditCaptionRequest(episode = "12", updDt = "2024-01-01T10:00", website = "https://a.com").validate(1)
        }
    }

    @Test
    fun `animeNo 가 0 이하이면 검증 실패`() {
        assertThrows(BadRequestException::class.java) { EditCaptionRequest().validate(0) }
    }

    @Test
    fun `화수가 숫자 형식이 아니면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            EditCaptionRequest(episode = "12화", updDt = "2024-01-01T10:00").validate(1)
        }
        assertEquals("화수는 숫자여야합니다.\n최대 (5자리.소수점2자리)", ex.message)
    }

    @Test
    fun `날짜 형식이 잘못되면 검증 실패`() {
        val ex = assertThrows(BadRequestException::class.java) {
            EditCaptionRequest(episode = "1", updDt = "2024/01/01").validate(1)
        }
        assertEquals("날짜형식이 잘못되었습니다.", ex.message)
    }

    @Test
    fun `미래 시각은 현재 시각으로 보정된다`() {
        val future = LocalDateTime.now().plusDays(3).format(DateFormats.CAPTION)
        val request = EditCaptionRequest(episode = "1", updDt = future)
        org.junit.jupiter.api.Assertions.assertTrue(
            request.updatedAt.isBefore(LocalDateTime.now().plusMinutes(1)),
        )
    }

    @Test
    fun `소수점 화수도 허용한다`() {
        assertDoesNotThrow {
            EditCaptionRequest(episode = "12.5", updDt = "2024-01-01T10:00").validate(1)
        }
    }
}
