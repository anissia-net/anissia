package anissia.translator.api.dto

import anissia.agenda.domain.Agenda
import anissia.agenda.domain.AgendaPoll
import anissia.support.Texts
import anissia.support.badRequestUnless

class TranslatorApplyPollItem(
    val no: Long = 0,
    val vote: Int = 0,
    val name: String? = null,
    val comment: String? = null,
    val regTime: Long = 0L,
) {
    constructor(agendaPoll: AgendaPoll) : this(
        no = agendaPoll.pollNo,
        vote = agendaPoll.voteUp + agendaPoll.voteDown,
        name = agendaPoll.name,
        comment = agendaPoll.comment,
        regTime = agendaPoll.regDt.toEpochSecond(),
    )
}

class TranslatorApplyItem(
    val applyNo: Long = 0,
    val status: String? = "",
    val result: String? = "",
    val name: String? = null,
    val website: String? = null,
    val regTime: Long = 0L,
    val polls: List<TranslatorApplyPollItem> = listOf(),
) {
    constructor(agenda: Agenda, includePolls: Boolean = false) : this(
        applyNo = agenda.agendaNo,
        status = agenda.status,
        result = agenda.data1,
        name = agenda.data2,
        website = agenda.data3,
        regTime = agenda.regDt.toEpochSecond(),
        polls = if (includePolls) agenda.polls.map { TranslatorApplyPollItem(it) }.sortedBy { it.no } else listOf(),
    )
}

data class AddApplyRequest(
    val website: String = "",
) {
    fun validate() {
        badRequestUnless(Texts.isWebSite(website, false)) {
            "사이트주소는 공백이거나 http:// https:// 로시작해야합니다."
        }
    }
}

data class NewApplyPollRequest(
    val point: String = "",
    val comment: String = "",
) {
    fun validate(applyNo: Long) {
        badRequestUnless(applyNo > 0) { "잘못된 번호" }
        badRequestUnless(POINT_REGEX.matches(point)) { "찬반의 코드 이상" }
        badRequestUnless(comment.isNotBlank()) { "의견을 입력해주세요." }
    }

    companion object {
        private val POINT_REGEX = Regex("-1|0|1|")
    }
}
