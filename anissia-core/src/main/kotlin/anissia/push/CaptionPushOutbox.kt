package anissia.push

import anissia.notification.service.AnimeNotificationsDisabledEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 네트워크 요청 없이 자막 저장 트랜잭션에 이벤트와 수신 기기 목록을 함께 기록한다. */
@Service
class CaptionPushOutbox(private val jdbc: JdbcTemplate, private val sender: PushSender) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun captionUpdated(
        animeNo: Long,
        translatorAn: Long,
        subject: String,
        translatorName: String,
        previousEpisode: String,
        previousWebsite: String,
        episode: String,
        website: String,
    ) {
        if (!isNewPublication(previousEpisode, previousWebsite, episode, website)) return
        val normalizedEpisode = normalizeEpisode(episode)
        val eventKey = PushValidation.hash("$animeNo:$translatorAn:$normalizedEpisode")
        val inserted = jdbc.update(
            """INSERT IGNORE INTO caption_push_event
                (event_key, anime_no, translator_an, episode, subject, translator_name, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))""",
            eventKey, animeNo, translatorAn, normalizedEpisode, subject, translatorName,
        )
        // 이미 발행한 회차는 URL 변경·날짜 수정·삭제 후 재등록·숫자 표기 변경으로 재발송하지 않는다.
        if (inserted == 0) return
        jdbc.update(
            """INSERT INTO notification_inbox (an, event_key, created_at)
                SELECT n.an, ?, CURRENT_TIMESTAMP(6) FROM anime_favorite n
                WHERE n.anime_no = ? AND n.notify = true""",
            eventKey, animeNo,
        )
        // 사이트 알림함은 기기 구독과 무관하게 저장하고, Web Push만 설정에 따라 발송한다.
        if (!sender.enabled) return
        jdbc.update(
            """INSERT INTO push_delivery
                (event_key, subscription_id, state, attempts, next_attempt_at, created_at)
                SELECT ?, s.id, 'pending', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
                FROM push_subscription s JOIN anime_favorite n ON n.an = s.an
                WHERE n.anime_no = ? AND n.notify = true
                AND (s.expiration_time IS NULL OR s.expiration_time > ?)""",
            eventKey, animeNo, System.currentTimeMillis(),
        )
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    fun notificationsDisabled(event: AnimeNotificationsDisabledEvent) {
        jdbc.update(
            """DELETE d FROM push_delivery d
                JOIN push_subscription s ON s.id = d.subscription_id
                JOIN caption_push_event e ON e.event_key = d.event_key
                WHERE s.an = ? AND e.anime_no = ? AND d.state = 'pending'""",
            event.an, event.animeNo,
        )
    }

    fun dueIds(): List<Long> = jdbc.query(
        """SELECT id FROM push_delivery WHERE state = 'pending' AND attempts < 6
            AND next_attempt_at <= CURRENT_TIMESTAMP(6) ORDER BY next_attempt_at, id LIMIT 20""",
        { rs, _ -> rs.getLong(1) },
    )

    /** 조건부 UPDATE가 프로세스 간 claim 역할을 한다. 프로세스가 죽으면 2분 뒤 다른 실행이 이어받는다. */
    fun claim(id: Long): ClaimedPushDelivery? {
        val token = UUID.randomUUID().toString()
        val claimed = jdbc.update(
            """UPDATE push_delivery SET claim_token = ?, attempts = attempts + 1,
                next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 2 MINUTE)
                WHERE id = ? AND state = 'pending' AND attempts < 6 AND next_attempt_at <= CURRENT_TIMESTAMP(6)""",
            token, id,
        )
        if (claimed == 0) return null
        return loadEligible(id, token) ?: run {
            finish(id, token, "cancelled")
            null
        }
    }

    /** 실제 전송 직전에도 작품 알림 옵션, 계정, 구독, 공개된 자막이 유효한지 다시 읽는다. */
    fun loadEligible(id: Long, token: String): ClaimedPushDelivery? = jdbc.query(
        """SELECT d.id, d.claim_token, d.attempts, e.event_key, e.anime_no, e.episode, e.subject,
            e.translator_name, s.id AS subscription_id, s.endpoint, s.p256dh, s.auth, s.client_id
            FROM push_delivery d
            JOIN caption_push_event e ON e.event_key = d.event_key
            JOIN push_subscription s ON s.id = d.subscription_id
            JOIN anime_favorite n ON n.an = s.an AND n.anime_no = e.anime_no AND n.notify = true
            JOIN account a ON a.an = s.an
            JOIN anime_caption c ON c.anime_no = e.anime_no AND c.an = e.translator_an AND c.website <> ''
            WHERE d.id = ? AND d.claim_token = ? AND d.state = 'pending'
            AND e.created_at > DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
            AND CAST(c.episode AS DECIMAL(7, 2)) >= CAST(e.episode AS DECIMAL(7, 2))
            AND (a.ban_expire_dt IS NULL OR a.ban_expire_dt <= CURRENT_TIMESTAMP(6))
            AND (s.expiration_time IS NULL OR s.expiration_time > ?)""",
        { rs, _ ->
            ClaimedPushDelivery(
                id = rs.getLong("id"), token = rs.getString("claim_token"), attempts = rs.getInt("attempts"),
                eventKey = rs.getString("event_key"), animeNo = rs.getLong("anime_no"), episode = rs.getString("episode"),
                subject = rs.getString("subject"), translatorName = rs.getString("translator_name"),
                subscriptionId = rs.getLong("subscription_id"), endpoint = rs.getString("endpoint"),
                p256dh = rs.getString("p256dh"), auth = rs.getString("auth"), clientId = rs.getString("client_id"),
            )
        }, id, token, System.currentTimeMillis(),
    ).singleOrNull()

    fun finish(id: Long, token: String, state: String) {
        jdbc.update(
            """UPDATE push_delivery SET state = ?, claim_token = NULL, finished_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND claim_token = ? AND state = 'pending'""", state, id, token,
        )
    }

    fun removeExpiredSubscription(delivery: ClaimedPushDelivery) {
        // 새 구독으로 교체된 뒤 도착한 이전 요청의 응답이 새 구독을 지우지 않게 한다.
        jdbc.update("DELETE FROM push_subscription WHERE id = ? AND client_id = ?", delivery.subscriptionId, delivery.clientId)
    }

    fun retry(delivery: ClaimedPushDelivery) {
        if (delivery.attempts >= 6) {
            finish(delivery.id, delivery.token, "failed")
            return
        }
        val delaySeconds = listOf(30, 120, 600, 3600, 21600)[delivery.attempts - 1]
        jdbc.update(
            """UPDATE push_delivery SET claim_token = NULL,
                next_attempt_at = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6))
                WHERE id = ? AND claim_token = ? AND state = 'pending'""", delaySeconds, delivery.id, delivery.token,
        )
    }

    fun cleanup() {
        jdbc.update(
            """UPDATE push_delivery SET state = 'failed', claim_token = NULL, finished_at = CURRENT_TIMESTAMP(6)
                WHERE state = 'pending' AND next_attempt_at <= CURRENT_TIMESTAMP(6)
                AND (attempts >= 6 OR created_at <= DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY))""",
        )
        jdbc.update(
            """DELETE FROM push_delivery WHERE state <> 'pending'
                AND finished_at < DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 30 DAY) LIMIT 100""",
        )
        jdbc.update("DELETE FROM push_subscription WHERE expiration_time IS NOT NULL AND expiration_time <= ?", System.currentTimeMillis())
        // caption_push_event는 회차 중복 방지 기록이므로 유지한다. 작품/번역자 삭제 때 FK로 함께 삭제된다.
    }

    companion object {
        fun normalizeEpisode(episode: String): String = episode.toBigDecimal().stripTrailingZeros().toPlainString()

        fun isNewPublication(previousEpisode: String, previousWebsite: String, episode: String, website: String): Boolean =
            website.isNotBlank() && (previousWebsite.isBlank() || episode.toBigDecimal() > previousEpisode.toBigDecimal())
    }
}

// endpoint/암호화 키가 자동 toString에 노출되지 않도록 data class로 만들지 않는다.
class ClaimedPushDelivery(
    val id: Long,
    val token: String,
    val attempts: Int,
    val eventKey: String,
    val animeNo: Long,
    val episode: String,
    val subject: String,
    val translatorName: String,
    val subscriptionId: Long,
    val endpoint: String,
    val p256dh: String,
    val auth: String,
    val clientId: String,
)
