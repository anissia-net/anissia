package anissia.anime.service

import anissia.anime.api.dto.AnimeScheduleItem
import anissia.anime.domain.AnimeStatus
import anissia.anime.repository.AnimeRepository
import anissia.external.analytics.GoogleAnalyticsClient
import anissia.security.Actor
import anissia.support.DateFormats
import anissia.support.badRequest
import anissia.support.badRequestUnless
import anissia.support.getOrLoad
import anissia.support.ttlCache
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.minutes

@Service
class AnimeScheduleService(
    private val animeRepository: AnimeRepository,
    private val googleAnalyticsClient: GoogleAnalyticsClient,
) {
    private val cache = ttlCache<String, List<AnimeScheduleItem>>(5.minutes)

    @Transactional(readOnly = true)
    fun get(week: String, useCache: Boolean, actor: Actor, userAgent: String?): List<AnimeScheduleItem> {
        badRequestUnless(week in ALLOWED_WEEKS) { "invalid week" }

        if (!useCache) {
            actor.validateAdmin()
            return load(week)
        }

        return cache.getOrLoad(week) { load(it) }
            .also { sendPageView("/api/anime/schedule/$week", actor, userAgent) }
    }

    @Transactional(readOnly = true)
    fun getSvg(width: String, color: String, actor: Actor, userAgent: String?): String {
        badRequestUnless(WIDTH_REGEX.matches(width)) { "invalid width" }
        badRequestUnless(COLOR_REGEX.matches(color)) { "invalid color" }

        val now = OffsetDateTime.now()
        val titleBgColor = color.substring(0, 6)
        val titleColor = color.substring(6, 12)
        val ymdBgColor = color.substring(12, 18)
        val ymdColor = color.substring(18, 24)
        val listBgColor = color.substring(24, 30)
        val listColor = color.substring(30, 36)
        val schedule = get("${now.dayOfWeek.value % 7}", true, actor, userAgent)
        val height = schedule.size * 20 + 50

        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
<svg version="1.1" id="Layer_1" xmlns="http://www.w3.org/2000/svg" x="0px" y="0px" width="${width}px" height="${height}px" viewBox="0 0 $width $height" enable-background="new 0 0 $width $height" xml:space="preserve">
<rect fill="#${listBgColor}" width="100%" height="100%"/>
<rect fill="#${titleBgColor}" width="100%" height="30" />
<rect fill="#${ymdBgColor}" width="100%" height="20" y="30" />
<text x="0" y="0" fill="#${listColor}" font-family="'Malgun Gothic'" font-size="13">
<tspan x="50%" dy="20" fill="#${titleColor}" text-anchor="middle" font-size="13" font-weight="bold">애니편성표</tspan>
<tspan x="50%" dy="25" fill="#${ymdColor}" text-anchor="middle" font-size="12">${now.format(DateFormats.USER_YMD)}</tspan>
${schedule.joinToString("\n") { """<tspan x="2" dy="20"><![CDATA[${it.time} ${it.subject}]]></tspan>""" }}
</text>
</svg>"""
    }

    private fun sendPageView(path: String, actor: Actor, userAgent: String?) {
        if (userAgent == null) {
            badRequest("does not exist user-agent")
        }
        googleAnalyticsClient.pageView(path, actor.ip, userAgent)
    }

    private fun load(week: String): List<AnimeScheduleItem> =
        animeRepository
            .findAllByStatusNotAndWeek(AnimeStatus.END, week)
            .map { AnimeScheduleItem(it) }
            .run {
                when (week) {
                    "7" -> sortedByDescending { if (it.time != "") it.time else "9999" }
                    "8" -> sortedBy { if (it.time != "") it.time else "9999" }
                    else -> sortedBy { it.time }
                }
            }

    companion object {
        private val ALLOWED_WEEKS = setOf("0", "1", "2", "3", "4", "5", "6", "7", "8")
        private val WIDTH_REGEX = Regex("""\d{3}""")
        private val COLOR_REGEX = Regex("""[a-f\d]{36}""")
    }
}
