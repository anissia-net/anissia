package anissia.notification.api.dto

import anissia.anime.api.dto.CaptionItem
import anissia.notification.domain.AnimeNotification

data class EditNotificationRequest(val notify: Boolean = true)

class NotificationItem(notification: AnimeNotification) {
    private val anime = requireNotNull(notification.anime)

    val animeNo: Long = anime.animeNo
    val subject: String = anime.subject
    val originalSubject: String = anime.originalSubject
    val status: String = anime.status.name
    val week: String = anime.week
    val time: String = anime.time
    val genres: String = anime.genres
    val startDate: String = anime.startDate
    val endDate: String = anime.endDate
    val website: String = anime.website
    val x: String = anime.x
    val note: String = anime.note
    val captionCount: Int = anime.captionCount
    val notify: Boolean = notification.notify
    val captions: List<CaptionItem> = anime.captions.sortedByDescending { it.updDt }.map { CaptionItem(it) }
}
