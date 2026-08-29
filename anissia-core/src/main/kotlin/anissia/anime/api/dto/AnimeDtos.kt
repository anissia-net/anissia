package anissia.anime.api.dto

import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeCaption
import anissia.anime.domain.AnimeStatus
import anissia.support.DateFormats
import anissia.support.Texts
import anissia.support.badRequestIf
import anissia.support.badRequestOnFailure
import anissia.support.badRequestUnless

private val TIME_REGEX = Regex("""\d{2}:\d{2}""")
private val WEEK_REGEX = Regex("[012345678]")

class AnimeCaptionItem(
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

class AnimeItem(
    val animeNo: Long = 0,
    val status: String = "",
    val week: String = "",
    val time: String = "",
    val subject: String = "",
    val originalSubject: String = "",
    val captionCount: Int = 0,
    val genres: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val website: String = "",
    val x: String = "",
    val note: String = "",
    var agendaNo: Long = 0,
    val captions: List<AnimeCaptionItem> = emptyList(),
) {
    constructor(anime: Anime, includeCaption: Boolean = false) : this(
        animeNo = anime.animeNo,
        status = anime.status.toString(),
        week = anime.week,
        time = anime.time,
        subject = anime.subject,
        originalSubject = anime.originalSubject,
        captionCount = anime.captionCount,
        genres = anime.genres,
        startDate = anime.startDate,
        endDate = anime.endDate,
        website = anime.website,
        x = anime.x,
        note = anime.note,
        captions = if (includeCaption) anime.captions.map { AnimeCaptionItem(it) } else emptyList(),
    )
}

class AnimeScheduleItem(
    val week: String,
    val animeNo: Long,
    val status: String,
    val time: String,
    val subject: String,
    val originalSubject: String,
    val genres: String,
    val captionCount: Int,
    val startDate: String,
    val endDate: String,
    val website: String,
    val x: String,
) {
    constructor(anime: Anime) : this(
        week = anime.week,
        animeNo = anime.animeNo,
        status = anime.status.toString(),
        time = if (!anime.week.matches("7|8".toRegex())) anime.time else anime.startDate,
        subject = anime.subject,
        originalSubject = anime.originalSubject,
        genres = anime.genres,
        captionCount = anime.captionCount,
        startDate = anime.startDate,
        endDate = anime.endDate,
        website = anime.website,
        x = anime.x,
    )
}

class AnimeRankItem(
    val animeNo: Long = 0,
    val subject: String = "",
    val hit: Long = 0,
    var rank: Int = 0,
    var diff: Int? = null,
) {
    val exist: Boolean get() = subject != ""
}

data class NewAnimeRequest(
    val status: String = "",
    val week: String = "",
    val time: String = "",
    val subject: String = "",
    val originalSubject: String = "",
    val genres: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val website: String = "",
    val x: String = "",
) {
    val genresList: List<String> get() = genres.split(",".toRegex())
    val statusEnum: AnimeStatus get() = AnimeStatus.valueOf(status)

    fun validate() {
        badRequestUnless(TIME_REGEX.matches(time)) { "잘못된 시간입니다." }
        badRequestUnless(WEEK_REGEX.matches(week)) { "잘못된 요일입니다." }
        validateAnimeFields(
            subject = subject,
            originalSubject = originalSubject,
            genres = genres,
            genresList = genresList,
            startDate = startDate,
            endDate = endDate,
            website = website,
            x = x,
        ) { statusEnum }
    }
}

data class EditAnimeRequest(
    val status: String = "",
    val week: String = "",
    val time: String = "",
    val subject: String = "",
    val originalSubject: String = "",
    val genres: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val website: String = "",
    val x: String = "",
    val note: String = "",
) {
    val genresList: List<String> get() = genres.split(",".toRegex())
    val statusEnum: AnimeStatus get() = AnimeStatus.valueOf(status)

    fun validate(animeNo: Long) {
        badRequestUnless(TIME_REGEX.matches(time)) { "잘못된 시간입니다." }
        badRequestUnless(WEEK_REGEX.matches(week)) { "잘못된 요일입니다." }
        badRequestUnless(animeNo > 0) { "animeNo 는 0 이상이어야 합니다." }
        validateAnimeFields(
            subject = subject,
            originalSubject = originalSubject,
            genres = genres,
            genresList = genresList,
            startDate = startDate,
            endDate = endDate,
            website = website,
            x = x,
        ) { statusEnum }
        badRequestIf(note.length > 512) { "비고는 512자 이하로 입력해주세요." }
    }
}

private inline fun validateAnimeFields(
    subject: String,
    originalSubject: String,
    genres: String,
    genresList: List<String>,
    startDate: String,
    endDate: String,
    website: String,
    x: String,
    statusEnum: () -> AnimeStatus,
) {
    badRequestIf(subject.isBlank()) { "애니메이션 제목을 입력해주세요." }
    badRequestIf(subject != subject.trim()) { "애니메이션 제목에 공백을 제거해 주세요." }
    badRequestIf(originalSubject != originalSubject.trim()) { "애니메이션 원제에 공백을 제거해 주세요." }
    badRequestOnFailure("존재하지 않는 상태입니다.") { statusEnum() }
    badRequestIf(genres.isBlank()) { "장르가 입력되지 않았습니다." }
    badRequestIf(genresList.size > 3) { "장르는 3개까지만 입력가능합니다." }
    badRequestIf(!Texts.isAnimeDate(startDate)) { "시작일이 규격에 맞지 않습니다." }
    badRequestIf(!Texts.isAnimeDate(endDate)) { "종료일이 규격에 맞지 않습니다." }
    badRequestIf(startDate.isNotEmpty() && endDate.isNotEmpty() && startDate > endDate) {
        "시작일은 종료일보다 미래일 수 없습니다."
    }
    badRequestIf(!Texts.isWebSite(website, true)) { "사이트주소는 공백이거나 http:// https:// 로시작해야합니다." }
    badRequestIf(!Texts.isWebSite(x, true)) { "X주소는 공백이거나 http:// https:// 로시작해야합니다." }
}
