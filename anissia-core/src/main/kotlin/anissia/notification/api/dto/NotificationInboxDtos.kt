package anissia.notification.api.dto

import java.time.Instant

data class NotificationInbox(val items: List<NotificationInboxItem>, val unreadCount: Long)

data class NotificationInboxItem(
    val id: Long,
    val animeNo: Long,
    val subject: String,
    val episode: String,
    val translatorName: String,
    val createdAt: Instant,
    val readAt: Instant?,
)
