package anissia.board.service

import anissia.account.service.AccountService
import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.board.api.dto.BoardTopicItem
import anissia.board.api.dto.EditTopicRequest
import anissia.board.api.dto.NewTopicRequest
import anissia.board.domain.BoardPost
import anissia.board.domain.BoardTopic
import anissia.board.repository.BoardPostRepository
import anissia.board.repository.BoardTopicRepository
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TopicService(
    private val boardTopicRepository: BoardTopicRepository,
    private val boardPostRepository: BoardPostRepository,
    private val boardService: BoardService,
    private val activePanelRepository: ActivePanelRepository,
    private val accountService: AccountService,
) {
    @Transactional(readOnly = true)
    fun get(ticker: String, topicNo: Long): BoardTopicItem =
        boardTopicRepository
            .findWithAccountByTickerAndTopicNo(ticker, topicNo)
            ?.let { BoardTopicItem(it, boardPostRepository.findAllWithAccountByTopicNoOrderByPostNo(it.topicNo)) }
            ?: BoardTopicItem()

    @Transactional(readOnly = true)
    fun getList(ticker: String, page: Int): Page<BoardTopicItem> =
        boardTopicRepository
            .findAllWithAccountByTickerOrderByTickerAscFixedDescTopicNoDesc(ticker, PageRequest.of(page, 20))
            .map { BoardTopicItem(it) }

    @Transactional(readOnly = true)
    fun getMainRecent(): Map<String, List<Map<String, Any>>> =
        mapOf("notice" to getRecent("notice"), "inquiry" to getRecent("inquiry"))

    private fun getRecent(ticker: String): List<Map<String, Any>> =
        boardTopicRepository
            .findTop5ByTickerAndFixedOrderByTopicNoDesc(ticker)
            .map {
                mapOf(
                    "topicNo" to it.topicNo,
                    "topic" to it.topic,
                    "postCount" to it.postCount,
                    "regTime" to it.regDt.toEpochSecond(),
                )
            }

    @Transactional
    fun add(ticker: String, request: NewTopicRequest, actor: Actor): ApiResponse<Long> {
        accountService.validateCriticalActor(actor)

        if (!boardService.canWriteTopic(ticker, actor.roles)) {
            return ApiResponse.fail("권한이 없습니다.", -1)
        }

        val topic = boardTopicRepository.saveAndFlush(
            BoardTopic.create(ticker = ticker, topic = request.topic, an = actor.an),
        )
        boardPostRepository.saveAndFlush(
            BoardPost.createRootPost(topicNo = topic.topicNo, content = request.content, an = actor.an),
        )
        return ApiResponse.ok(topic.topicNo)
    }

    @Transactional
    fun edit(topicNo: Long, request: EditTopicRequest, actor: Actor): ApiResponse<Unit> {
        request.validate(topicNo)
        actor.validateLogin()

        val topic = boardTopicRepository.findByIdOrNull(topicNo)?.takeIf { it.an == actor.an }
            ?: return ApiResponse.fail("권한이 없거나 존재하지 않는 글입니다.")

        boardPostRepository
            .findWithAccountByTopicNoAndRootIsTrue(topicNo)
            ?.also { boardPostRepository.save(it.apply { edit(request.content) }) }
        boardTopicRepository.save(topic.apply { edit(request.topic) })

        return ApiResponse.ok()
    }

    @Transactional
    fun delete(topicNo: Long, actor: Actor): ApiResponse<Unit> {
        if (topicNo <= 0) {
            return ApiResponse.fail("권한이 없거나 존재하지 않는 글입니다.")
        }
        actor.validateLogin()

        val topic = boardTopicRepository.findByIdOrNull(topicNo)
            ?.takeIf { it.an == actor.an || actor.isAdmin }
            ?: return ApiResponse.fail("권한이 없거나 존재하지 않는 글입니다.")

        if (topic.an != actor.an) {
            activePanelRepository.save(
                ActivePanel(
                    published = false,
                    code = "DEL",
                    an = actor.an,
                    data1 = "[${actor.name}]님이 글을 삭제했습니다.",
                    data2 = "작성자/회원번호: ${topic.account?.name}/${topic.an}",
                    data3 = "${topic.topic}\n${
                        boardPostRepository.findWithAccountByTopicNoAndRootIsTrue(topic.topicNo)?.content
                    }",
                ),
            )
        }
        boardPostRepository.deleteAllByTopicNo(topicNo)
        boardTopicRepository.delete(topic)

        return ApiResponse.ok()
    }
}
