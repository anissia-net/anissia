package anissia.notification.service

import anissia.notification.api.dto.NotificationInbox
import anissia.notification.api.dto.NotificationInboxItem
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.badRequestUnless
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NotificationInboxService(private val jdbc: JdbcTemplate) {
    @Transactional(readOnly = true)
    fun getInbox(actor: Actor): NotificationInbox {
        actor.validateLogin()
        val unreadCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_inbox WHERE an = ? AND read_at IS NULL",
            Long::class.java, actor.an,
        ) ?: 0L
        val items = jdbc.query(
            // CURRENT_TIMESTAMP로 저장한 DB 시각을 DB 세션 시간대로 해석한다.
            // JDBC/JVM 기본 시간대와 브라우저 시간대가 달라도 같은 절대 시각을 전달한다.
            """SELECT n.id, e.anime_no, e.subject, e.episode, e.translator_name,
                UNIX_TIMESTAMP(n.created_at) * 1000 AS created_at_ms,
                UNIX_TIMESTAMP(n.read_at) * 1000 AS read_at_ms
                FROM notification_inbox n JOIN caption_push_event e ON e.event_key = n.event_key
                WHERE n.an = ? ORDER BY n.id DESC LIMIT 50""",
            { rs, _ ->
                NotificationInboxItem(
                    id = rs.getLong("id"), animeNo = rs.getLong("anime_no"), subject = rs.getString("subject"),
                    episode = rs.getString("episode"), translatorName = rs.getString("translator_name"),
                    createdAt = Instant.ofEpochMilli(rs.getBigDecimal("created_at_ms").toLong()),
                    readAt = rs.getBigDecimal("read_at_ms")?.toLong()?.let(Instant::ofEpochMilli),
                )
            }, actor.an,
        )
        return NotificationInbox(items, unreadCount)
    }

    @Transactional
    fun markRead(id: Long, actor: Actor): ApiResponse<Unit> {
        actor.validateLogin()
        badRequestUnless(id > 0) { "id 는 0보다 커야 합니다." }
        // 다른 계정의 ID 여부를 드러내지 않으며, 다시 읽어도 최초 읽은 시각을 유지한다.
        jdbc.update(
            "UPDATE notification_inbox SET read_at = CURRENT_TIMESTAMP(6) WHERE id = ? AND an = ? AND read_at IS NULL",
            id, actor.an,
        )
        return ApiResponse.ok()
    }

    @Transactional
    fun markAllRead(actor: Actor): ApiResponse<Unit> {
        actor.validateLogin()
        jdbc.update(
            "UPDATE notification_inbox SET read_at = CURRENT_TIMESTAMP(6) WHERE an = ? AND read_at IS NULL",
            actor.an,
        )
        return ApiResponse.ok()
    }
}
