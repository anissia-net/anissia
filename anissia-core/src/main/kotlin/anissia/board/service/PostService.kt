package anissia.board.service

import anissia.account.service.AccountService
import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.board.api.dto.EditPostRequest
import anissia.board.api.dto.NewPostRequest
import anissia.board.domain.BoardPost
import anissia.board.repository.BoardPostRepository
import anissia.board.repository.BoardTopicRepository
import anissia.counter.DerivedCounters
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PostService(
    private val boardPostRepository: BoardPostRepository,
    private val boardTopicRepository: BoardTopicRepository,
    private val derivedCounters: DerivedCounters,
    private val boardService: BoardService,
    private val activePanelRepository: ActivePanelRepository,
    private val accountService: AccountService,
) {
    @Transactional
    fun add(topicNo: Long, request: NewPostRequest, actor: Actor): ApiResponse<Unit> {
        request.validate(topicNo)
        actor.validateLogin()
        accountService.validateCriticalActor(actor)

        val topic = boardTopicRepository.findByIdOrNull(topicNo)
            ?.takeIf { boardService.canWritePost(it.ticker, actor.roles) }
            ?: return ApiResponse.fail("권한이 없거나 존재하지 않는 글 혹은 게시판입니다.")

        boardPostRepository.save(
            BoardPost.create(topicNo = topic.topicNo, content = request.content, an = actor.an),
        )
        derivedCounters.markTopic(topicNo)

        return ApiResponse.ok()
    }

    @Transactional
    fun edit(postNo: Long, request: EditPostRequest, actor: Actor): ApiResponse<Unit> {
        request.validate(postNo)
        actor.validateLogin()

        val post = boardPostRepository.findByIdOrNull(postNo)
            ?.takeIf { !it.root && it.an == actor.an }
            ?: return ApiResponse.fail("권한이 없거나 존재하지 않는 글입니다.")

        boardPostRepository.save(post.apply { edit(request.content) })
        derivedCounters.markTopic(post.topicNo)

        return ApiResponse.ok()
    }

    @Transactional
    fun delete(postNo: Long, actor: Actor): ApiResponse<Unit> {
        if (postNo <= 0) {
            return ApiResponse.fail("권한이 없거나 존재하지 않는 글입니다.")
        }
        actor.validateLogin()

        val post = boardPostRepository.findByIdOrNull(postNo)
            ?.takeIf { !it.root && (it.an == actor.an || actor.isAdmin) }
            ?: return ApiResponse.fail("권한이 없거나 존재하지 않는 글입니다.")

        if (post.an != actor.an) {
            activePanelRepository.save(
                ActivePanel(
                    published = false,
                    code = "DEL",
                    an = actor.an,
                    data1 = "[${actor.name}]님이 댓글을 삭제했습니다.",
                    data2 = "작성자/회원번호: ${post.account?.name}/${post.an}",
                    data3 = post.content,
                ),
            )
        }
        boardPostRepository.delete(post)
        derivedCounters.markTopic(post.topicNo)

        return ApiResponse.ok()
    }
}
