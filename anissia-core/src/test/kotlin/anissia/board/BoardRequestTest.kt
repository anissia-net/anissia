package anissia.board

import anissia.board.api.dto.EditPostRequest
import anissia.board.api.dto.EditTopicRequest
import anissia.board.api.dto.NewPostRequest
import anissia.support.FailException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("게시판 요청 DTO 검증")
class BoardRequestTest {

    @Test
    fun `NewPostRequest - topicNo 가 0 이하이면 기본 메시지로 실패한다`() {
        val ex = assertThrows(FailException::class.java) { NewPostRequest(content = "abc").validate(0) }
        assertEquals("알수없는 오류입니다.", ex.message)
    }

    @Test
    fun `NewPostRequest - content 가 비면 내용 메시지로 실패한다`() {
        val ex = assertThrows(FailException::class.java) { NewPostRequest(content = "  ").validate(1) }
        assertEquals("내용을 입력해 주세요.", ex.message)
    }

    @Test
    fun `NewPostRequest - 정상이면 통과`() {
        assertDoesNotThrow { NewPostRequest(content = "hi").validate(1) }
    }

    @Test
    fun `EditPostRequest - postNo 가 0 이하이면 실패한다`() {
        val ex = assertThrows(FailException::class.java) { EditPostRequest(content = "c").validate(0) }
        assertEquals("알수없는 오류입니다.", ex.message)
    }

    @Test
    fun `EditTopicRequest - content 가 비면 실패한다`() {
        val ex = assertThrows(FailException::class.java) { EditTopicRequest(topic = "t").validate(1) }
        assertEquals("내용을 입력해 주세요.", ex.message)
    }

    @Test
    fun `EditTopicRequest - 정상이면 통과`() {
        assertDoesNotThrow { EditTopicRequest(topic = "t", content = "c").validate(1) }
    }
}
