package anissia.board.api.dto

import anissia.board.domain.BoardPost
import anissia.board.domain.BoardTicker
import anissia.board.domain.BoardTopic
import anissia.support.fail
import anissia.support.failIf
import com.fasterxml.jackson.annotation.JsonInclude

class BoardTickerItem(
    val ticker: String = "",
    val name: String = "",
    val writeTopicRoles: List<String> = listOf(),
    val writePostRoles: List<String> = listOf(),
    val phTopic: String = "",
) {
    constructor(boardTicker: BoardTicker) : this(
        ticker = boardTicker.ticker,
        name = boardTicker.name,
        writeTopicRoles = boardTicker.writeTopicRoles.split(",".toRegex()).filter { it != "" },
        writePostRoles = boardTicker.writePostRoles.split(",".toRegex()).filter { it != "" },
        phTopic = boardTicker.phTopic,
    )
}

class BoardPostItem(
    val postNo: Long = 0,
    val topicNo: Long = 0,
    val root: Boolean = false,
    val content: String = "",
    val name: String = "",
    val regTime: Long = 0L,
    val updTime: Long = 0L,
) {
    constructor(boardPost: BoardPost) : this(
        postNo = boardPost.postNo,
        topicNo = boardPost.topicNo,
        root = boardPost.root,
        content = boardPost.content,
        name = boardPost.account?.name ?: "탈퇴회원",
        regTime = boardPost.regDt.toEpochSecond(),
        updTime = boardPost.updDt.toEpochSecond(),
    )
}

class BoardTopicItem(
    val topicNo: Long = 0,
    val fixed: Boolean = false,
    val topic: String = "",
    val postCount: Int = 0,
    val regTime: Long = 0L,
    val name: String = "",
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val posts: List<BoardPostItem>? = null,
) {
    constructor(boardTopic: BoardTopic, posts: List<BoardPost>? = null) : this(
        topicNo = boardTopic.topicNo,
        fixed = boardTopic.fixed,
        topic = boardTopic.topic,
        postCount = boardTopic.postCount,
        regTime = boardTopic.regDt.toEpochSecond(),
        name = boardTopic.account?.name ?: "탈퇴회원",
        posts = posts?.map { BoardPostItem(it) },
    )
}

data class NewTopicRequest(
    val topic: String = "",
    val content: String = "",
)

data class EditTopicRequest(
    val topic: String = "",
    val content: String = "",
) {
    fun validate(topicNo: Long) {
        if (topicNo <= 0) fail(DEFAULT_FAIL)
        failIf(content.isBlank()) { "내용을 입력해 주세요." }
    }
}

data class NewPostRequest(
    val content: String = "",
) {
    fun validate(topicNo: Long) {
        if (topicNo <= 0) fail(DEFAULT_FAIL)
        failIf(content.isBlank()) { "내용을 입력해 주세요." }
    }
}

data class EditPostRequest(
    val content: String = "",
) {
    fun validate(postNo: Long) {
        if (postNo <= 0) fail(DEFAULT_FAIL)
        failIf(content.isBlank()) { "내용을 입력해 주세요." }
    }
}

private const val DEFAULT_FAIL = "알수없는 오류입니다."
