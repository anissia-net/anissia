package anissia.link

import anissia.anime.repository.AnimeRepository
import anissia.board.repository.BoardTopicRepository
import anissia.support.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class LinkTitle(val title: String?)

/** Public detail labels only. Each lookup selects one column, without loading a document or its relations. */
@RestController
@RequestMapping("/link")
class LinkTitleController(private val anime: AnimeRepository, private val topics: BoardTopicRepository) {
    @GetMapping("/title")
    fun title(@RequestParam kind: String, @RequestParam id: Long, response: HttpServletResponse): ApiResponse<LinkTitle> {
        response.setHeader("Cache-Control", "no-store")
        val title = if (id <= 0) null else when (kind) {
            "anime" -> anime.findSubjectByAnimeNo(id)
            "notice", "inquiry" -> topics.findTitleByTickerAndTopicNo(kind, id)
            else -> null
        }
        return ApiResponse.ok(LinkTitle(title))
    }
}
