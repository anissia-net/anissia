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
import anissia.search.SearchChangedEvent
import anissia.search.SearchKind
import org.springframework.context.ApplicationEventPublisher
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.PageImpl
import anissia.search.ContentSearchRepository
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
    private val events: ApplicationEventPublisher,
    private val search: ContentSearchRepository,
) {
    @Transactional(readOnly = true)
    fun get(ticker: String, topicNo: Long): BoardTopicItem =
        boardTopicRepository
            .findWithAccountByTickerAndTopicNo(ticker, topicNo)
            ?.let { BoardTopicItem(it, boardPostRepository.findAllWithAccountByTopicNoOrderByPostNo(it.topicNo)) }
            ?: BoardTopicItem()

    @Transactional(readOnly = true)
    fun getList(ticker: String, page: Int, q: String = ""): Page<BoardTopicItem> {
        val pageable = PageRequest.of(page, 20)
        if (q.isBlank()) return boardTopicRepository.findAllWithAccountByTickerOrderByTickerAscFixedDescTopicNoDesc(ticker, pageable).map { BoardTopicItem(it) }
        val found = search.search(SearchKind.BOARD, q, page, 20, mapOf("ticker" to ticker))
        val byId = boardTopicRepository.findAllByTopicNoIn(found.ids.map(String::toLong)).filter { it.ticker == ticker }.associateBy { it.topicNo.toString() }
        return PageImpl(found.ids.mapNotNull { byId[it]?.let(::BoardTopicItem) }, pageable, found.total)
    }

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
        request.validate()
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
        events.publishEvent(SearchChangedEvent(SearchKind.BOARD, topic.topicNo))
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

        events.publishEvent(SearchChangedEvent(SearchKind.BOARD, topicNo))
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

        events.publishEvent(SearchChangedEvent(SearchKind.BOARD, topicNo))
        return ApiResponse.ok()
    }
}
