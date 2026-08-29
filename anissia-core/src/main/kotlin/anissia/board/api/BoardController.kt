package anissia.board.api

import anissia.board.api.dto.BoardTickerItem
import anissia.board.api.dto.BoardTopicItem
import anissia.board.api.dto.EditPostRequest
import anissia.board.api.dto.EditTopicRequest
import anissia.board.api.dto.NewPostRequest
import anissia.board.api.dto.NewTopicRequest
import anissia.board.service.BoardService
import anissia.board.service.PostService
import anissia.board.service.TopicService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/board")
class BoardController(
    private val boardService: BoardService,
    private val topicService: TopicService,
    private val postService: PostService,
) {
    @GetMapping("/ticker/{ticker}")
    fun getTicker(@PathVariable ticker: String): ApiResponse<BoardTickerItem> =
        ApiResponse.ok(boardService.getTicker(ticker))

    @GetMapping("/topic/{ticker}/{topicNo}")
    fun getTopic(@PathVariable ticker: String, @PathVariable topicNo: Long): ApiResponse<BoardTopicItem> =
        ApiResponse.ok(topicService.get(ticker, topicNo))

    @GetMapping("/list/{ticker}/{page}")
    fun getList(@PathVariable ticker: String, @PathVariable page: Int): ApiResponse<Page<BoardTopicItem>> =
        ApiResponse.ok(topicService.getList(ticker, page))

    @GetMapping("/recent/home")
    fun getHomeRecent(): ApiResponse<Map<String, List<Map<String, Any>>>> =
        ApiResponse.ok(topicService.getMainRecent())

    @PostMapping("/topic/{ticker}")
    fun newTopic(
        @PathVariable ticker: String,
        @RequestBody request: NewTopicRequest,
        actor: Actor,
    ): ApiResponse<Long> = topicService.add(ticker, request, actor)

    @PutMapping("/topic/{topicNo}")
    fun editTopic(
        @PathVariable topicNo: Long,
        @RequestBody request: EditTopicRequest,
        actor: Actor,
    ): ApiResponse<Unit> = topicService.edit(topicNo, request, actor)

    @DeleteMapping("/topic/{topicNo}")
    fun deleteTopic(@PathVariable topicNo: Long, actor: Actor): ApiResponse<Unit> =
        topicService.delete(topicNo, actor)

    @PostMapping("/post/{topicNo}")
    fun newPost(
        @PathVariable topicNo: Long,
        @RequestBody request: NewPostRequest,
        actor: Actor,
    ): ApiResponse<Unit> = postService.add(topicNo, request, actor)

    @PutMapping("/post/{postNo}")
    fun editPost(
        @PathVariable postNo: Long,
        @RequestBody request: EditPostRequest,
        actor: Actor,
    ): ApiResponse<Unit> = postService.edit(postNo, request, actor)

    @DeleteMapping("/post/{postNo}")
    fun deletePost(@PathVariable postNo: Long, actor: Actor): ApiResponse<Unit> =
        postService.delete(postNo, actor)
}
