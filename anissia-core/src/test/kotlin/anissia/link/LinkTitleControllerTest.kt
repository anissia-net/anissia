package anissia.link

import anissia.anime.repository.AnimeRepository
import anissia.board.repository.BoardTopicRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class LinkTitleControllerTest {
    private val anime = mock(AnimeRepository::class.java)
    private val topics = mock(BoardTopicRepository::class.java)
    private val mvc = MockMvcBuilders.standaloneSetup(LinkTitleController(anime, topics)).build()

    @Test
    fun `returns only a public title from the matching collection`() {
        `when`(anime.findSubjectByAnimeNo(11)).thenReturn("작품 제목")
        `when`(topics.findTitleByTickerAndTopicNo("notice", 12)).thenReturn("공지 제목")
        for ((kind, id, title) in listOf(Triple("anime", 11, "작품 제목"), Triple("notice", 12, "공지 제목"))) {
            mvc.perform(get("/link/title").param("kind", kind).param("id", id.toString()))
                .andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("ok")).andExpect(jsonPath("$.data.title").value(title))
        }
        verify(anime).findSubjectByAnimeNo(11)
        verify(topics).findTitleByTickerAndTopicNo("notice", 12)
        verifyNoMoreInteractions(anime, topics)
    }

    @Test
    fun `unsupported collections and invalid IDs do not query content`() {
        for ((kind, id) in listOf("template" to "1", "admin" to "1", "anime" to "0", "notice" to "-1")) {
            mvc.perform(get("/link/title").param("kind", kind).param("id", id))
                .andExpect(status().isOk).andExpect(jsonPath("$.data.title").doesNotExist())
        }
        mvc.perform(get("/link/title").param("kind", "anime").param("id", "999999999999999999999"))
            .andExpect(status().isBadRequest)
        verifyNoInteractions(anime, topics)
    }

    @Test
    fun `a missing topic or mismatched board keeps the original link label`() {
        mvc.perform(get("/link/title").param("kind", "inquiry").param("id", "12"))
            .andExpect(status().isOk).andExpect(jsonPath("$.data.title").doesNotExist())
        verify(topics).findTitleByTickerAndTopicNo("inquiry", 12)
        verifyNoInteractions(anime)
    }
}
