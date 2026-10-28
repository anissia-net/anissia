package anissia.wiki

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class WikiContentControllerTest {
    @Test
    fun `anime content reset is not exposed as an API`() {
        val service = mock(WikiContentService::class.java)
        val mvc = MockMvcBuilders.standaloneSetup(WikiContentController(service)).build()

        mvc.perform(delete("/wiki/anime/11")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"baseRevision":1}"""))
            .andExpect(status().isMethodNotAllowed)

        verifyNoInteractions(service)
    }
}
