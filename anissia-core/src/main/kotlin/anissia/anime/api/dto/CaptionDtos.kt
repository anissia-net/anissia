package anissia.anime.api.dto

import anissia.anime.domain.AnimeCaption
import anissia.support.DateFormats
import anissia.support.Texts
import anissia.support.badRequestOnFailure
import anissia.support.badRequestUnless
import java.time.LocalDateTime
import java.time.OffsetDateTime

private val EPISODE_REGEX = Regex("""\d{1,5}|\d{1,5}\.\d{1,2}""")

class CaptionItem(
    val episode: String = "",
    val updDt: String = "",
    val website: String = "",
    val name: String = "",
) {
    constructor(animeCaption: AnimeCaption) : this(
        episode = animeCaption.episode,
        updDt = animeCaption.updDt.format(DateFormats.CAPTION) + ":00",
        website = animeCaption.website,
        name = animeCaption.account?.name ?: "탈퇴회원",
    )
}

class CaptionRecentItem(
    val animeNo: Long = 0,
    val subject: String = "",
    val episode: String = "",
    val updDt: String = "",
    val website: String = "",
    val name: String = "",
) {
    constructor(animeCaption: AnimeCaption) : this(
        animeNo = animeCaption.anime?.animeNo ?: 0,
        subject = animeCaption.anime!!.subject,
        episode = animeCaption.episode,
        updDt = animeCaption.updDt.format(DateFormats.CAPTION) + ":00",
        website = animeCaption.website,
        name = animeCaption.account!!.name,
    )
}

class MyCaptionItem(
    val animeNo: Long = 0,
    val subject: String = "",
    val episode: String = "0",
    val updDt: String = OffsetDateTime.now().format(DateFormats.CAPTION),
    val website: String = "",
) {
    constructor(animeCaption: AnimeCaption) : this(
        animeNo = animeCaption.anime?.animeNo ?: 0,
        subject = animeCaption.anime?.subject ?: "제목없음",
        episode = animeCaption.episode,
        updDt = animeCaption.updDt.format(DateFormats.CAPTION),
        website = animeCaption.website,
    )
}

data class EditCaptionRequest(
    val episode: String = "0",
    val updDt: String = LocalDateTime.now().format(DateFormats.CAPTION),
    val website: String = "",
) {
    val updatedAt: LocalDateTime
        get() = LocalDateTime.parse(updDt, DateFormats.CAPTION)
            .takeIf { it.isBefore(LocalDateTime.now()) }
            ?: LocalDateTime.now()

    fun validate(animeNo: Long) {
        badRequestUnless(animeNo > 0) { "animeNo 는 0 이상이어야 합니다." }
        badRequestUnless(Texts.isWebSite(website, true)) {
            "사이트주소는 공백이거나 http:// https:// 로시작해야합니다."
        }
        badRequestOnFailure("날짜형식이 잘못되었습니다.") { updatedAt }
        badRequestUnless(EPISODE_REGEX.matches(episode)) {
            "화수는 숫자여야합니다.\n최대 (5자리.소수점2자리)"
        }
    }
}
