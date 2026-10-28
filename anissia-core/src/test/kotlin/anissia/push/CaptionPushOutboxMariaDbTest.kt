package anissia.push

import anissia.notification.service.AnimeNotificationsDisabledEvent
import anissia.notification.service.NotificationInboxService
import anissia.security.Actor
import anissia.support.FailException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.DefaultTransactionDefinition
import java.util.UUID
import java.time.Instant

/**
 * Opt in with ANISSIA_TEST_DB_URL=jdbc:mariadb://127.0.0.1:3306/anissia and
 * ANISSIA_TEST_DB_USER / ANISSIA_TEST_DB_PASSWORD. Requires all three notification migration SQL files.
 * Every fixture remains uncommitted and is rolled back; no dispatcher or real push endpoint is used.
 */
@EnabledIfEnvironmentVariable(named = "ANISSIA_TEST_DB_URL", matches = "jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/[a-zA-Z0-9_]+")
class CaptionPushOutboxMariaDbTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var transactions: DataSourceTransactionManager
    private lateinit var transaction: TransactionStatus
    private lateinit var sender: PushSender
    private lateinit var outbox: CaptionPushOutbox
    private var account = 0L
    private var anime = 0L
    private var subscription = 0L
    private val clientId = UUID.randomUUID().toString()

    @BeforeEach
    fun beginFixtureTransaction() {
        val dataSource = DriverManagerDataSource(
            System.getenv("ANISSIA_TEST_DB_URL"),
            requireNotNull(System.getenv("ANISSIA_TEST_DB_USER")),
            requireNotNull(System.getenv("ANISSIA_TEST_DB_PASSWORD")),
        )
        jdbc = JdbcTemplate(dataSource)
        transactions = DataSourceTransactionManager(dataSource)
        transaction = transactions.getTransaction(DefaultTransactionDefinition())
        sender = mock(PushSender::class.java)
        `when`(sender.enabled).thenReturn(true)
        outbox = CaptionPushOutbox(jdbc, sender)
        val unique = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO account (email, password, name, reg_dt, last_login_dt)
                VALUES (?, 'not-a-login-password', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            "$unique@outbox.invalid", "outbox-${unique.take(8)}",
        )
        account = scalar("SELECT LAST_INSERT_ID()")
        jdbc.update(
            """INSERT INTO anime (status, week, time, subject, original_subject, autocorrect, genres,
                start_date, end_date, website, x, note, caption_count, upd_dt)
                VALUES ('ON', '8', '', ?, '', '', '', '', '', '', '', '', 1, CURRENT_TIMESTAMP)""",
            "outbox-test-$unique",
        )
        anime = scalar("SELECT LAST_INSERT_ID()")
        jdbc.update(
            "INSERT INTO anime_caption (anime_no, an, episode, upd_dt, website) VALUES (?, ?, '1', CURRENT_TIMESTAMP, ?)",
            anime, account, "https://caption.invalid/test",
        )
        jdbc.update("INSERT INTO anime_favorite (an, anime_no, notify) VALUES (?, ?, true)", account, anime)
        val endpoint = "https://outbox-test.invalid/$unique"
        jdbc.update(
            """INSERT INTO push_subscription
                (an, endpoint_hash, endpoint, p256dh, auth, client_id, created_at, updated_at)
                VALUES (?, ?, ?, 'unused-key', 'unused-auth', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
            account, PushValidation.hash(endpoint), endpoint, clientId,
        )
        subscription = scalar("SELECT LAST_INSERT_ID()")
    }

    @AfterEach
    fun rollbackAllFixtures() {
        if (::transaction.isInitialized) transactions.rollback(transaction)
        if (account > 0) assertEquals(0, scalar("SELECT COUNT(*) FROM account WHERE an = ?", account))
        if (anime > 0) assertEquals(0, scalar("SELECT COUNT(*) FROM anime WHERE anime_no = ?", anime))
    }

    private fun scalar(sql: String, vararg args: Any): Long =
        requireNotNull(jdbc.queryForObject(sql, Long::class.java, *args))

    private fun publish(previousEpisode: String, previousWebsite: String, episode: String, website: String) {
        outbox.captionUpdated(anime, account, "테스트 작품", "테스트 제작자", previousEpisode, previousWebsite, episode, website)
    }

    private fun publishEpisode(episode: Int): Long {
        jdbc.update("UPDATE anime_caption SET episode = ?, website = ? WHERE anime_no = ? AND an = ?", "$episode", "https://caption.invalid/$episode", anime, account)
        publish("0", "", "$episode", "https://caption.invalid/$episode")
        return scalar("SELECT id FROM push_delivery WHERE subscription_id = ? AND event_key = ?", subscription, PushValidation.hash("$anime:$account:$episode"))
    }

    private fun countEvents() = scalar("SELECT COUNT(*) FROM caption_push_event WHERE anime_no = ?", anime)
    private fun countDeliveries() = scalar("SELECT COUNT(*) FROM push_delivery WHERE subscription_id = ?", subscription)
    private fun countInbox() = scalar("SELECT COUNT(*) FROM notification_inbox WHERE an = ?", account)
    private fun state(id: Long) = jdbc.queryForObject("SELECT state FROM push_delivery WHERE id = ?", String::class.java, id)

    @Test
    fun `first publication and new episode create deliveries once but edits do not`() {
        publish("0", "", "0", "")
        assertEquals(0, countEvents())
        publishEpisode(1)
        assertEquals(1, countEvents())
        assertEquals(1, countDeliveries())
        assertEquals(1, countInbox())
        publish("0", "", "0001.00", "https://caption.invalid/republication")
        publish("1", "https://caption.invalid/1", "1", "https://caption.invalid/correction")
        publish("1", "https://caption.invalid/1", "0", "https://caption.invalid/0")
        publish("1", "https://caption.invalid/1", "2", "")
        assertEquals(1, countEvents())
        assertEquals(1, countDeliveries())
        assertEquals(1, countInbox())
        publishEpisode(2)
        assertEquals(2, countEvents())
        assertEquals(2, countDeliveries())
        assertEquals(2, countInbox())
    }

    @Test
    fun `inbox is recorded without web push and disabled recipients are excluded`() {
        `when`(sender.enabled).thenReturn(false)
        publish("0", "", "1", "https://caption.invalid/1")
        assertEquals(1, countEvents())
        assertEquals(1, countInbox())
        assertEquals(0, countDeliveries())
        jdbc.update("UPDATE anime_favorite SET notify = false WHERE an = ? AND anime_no = ?", account, anime)
        publish("1", "https://caption.invalid/1", "2", "https://caption.invalid/2")
        assertEquals(2, countEvents())
        assertEquals(1, countInbox())
        assertEquals(0, countDeliveries())
    }

    @Test
    fun `inbox listing and read operations isolate accounts and preserve first read time`() {
        publishEpisode(1)
        publishEpisode(2)
        val service = NotificationInboxService(jdbc)
        val actor = Actor(an = account)
        val otherActor = Actor(an = account + 1)
        val initial = service.getInbox(actor)
        assertEquals(2, initial.unreadCount)
        assertEquals(listOf("2", "1"), initial.items.map { it.episode })
        assertTrue(initial.items.all { it.animeNo == anime && it.subject == "테스트 작품" && it.readAt == null })
        assertTrue(service.getInbox(otherActor).items.isEmpty())
        assertEquals(0, service.getInbox(otherActor).unreadCount)
        service.markRead(initial.items.first().id, otherActor)
        service.markAllRead(otherActor)
        assertEquals(2, service.getInbox(actor).unreadCount)
        service.markRead(initial.items.first().id, actor)
        val once = service.getInbox(actor)
        assertEquals(1, once.unreadCount)
        assertNotNull(once.items.first().readAt)
        service.markRead(initial.items.first().id, actor)
        assertEquals(once.items.first().readAt, service.getInbox(actor).items.first().readAt)
        service.markAllRead(actor)
        assertEquals(0, service.getInbox(actor).unreadCount)
        assertEquals(2, service.getInbox(actor).items.size)
        assertThrows<FailException> { service.getInbox(Actor()) }
        assertThrows<FailException> { service.markRead(initial.items.first().id, Actor()) }
        assertThrows<FailException> { service.markAllRead(Actor()) }
    }

    @Test
    fun `unread total includes entries beyond the latest fifty and records survive opting out`() {
        `when`(sender.enabled).thenReturn(false)
        for (episode in 1..51) publish("${episode - 1}", "https://caption.invalid/previous", "$episode", "https://caption.invalid/$episode")
        val service = NotificationInboxService(jdbc)
        val actor = Actor(an = account)
        val inbox = service.getInbox(actor)
        assertEquals(51, inbox.unreadCount)
        assertEquals(50, inbox.items.size)
        assertEquals("51", inbox.items.first().episode)
        assertEquals("2", inbox.items.last().episode)
        jdbc.update("DELETE FROM anime_favorite WHERE an = ? AND anime_no = ?", account, anime)
        outbox.notificationsDisabled(AnimeNotificationsDisabledEvent(account, anime))
        assertEquals(51, service.getInbox(actor).unreadCount)
        service.markAllRead(actor)
        assertEquals(0, service.getInbox(actor).unreadCount)
        assertEquals(51, countInbox())
    }

    @Test
    fun `inbox timestamps use the database timezone instead of the JVM default timezone`() {
        publishEpisode(1)
        val previousZone = jdbc.queryForObject("SELECT @@session.time_zone", String::class.java)
        try {
            jdbc.update("SET time_zone = ?", "+02:00")
            jdbc.update(
                "UPDATE notification_inbox SET created_at = '2026-10-26 12:34:56.123456', read_at = '2026-10-26 13:34:56.654321' WHERE an = ?",
                account,
            )
            val entry = NotificationInboxService(jdbc).getInbox(Actor(an = account)).items.single()
            assertEquals(Instant.parse("2026-10-26T10:34:56.123Z"), entry.createdAt)
            assertEquals(Instant.parse("2026-10-26T11:34:56.654Z"), entry.readAt)
        } finally {
            jdbc.update("SET time_zone = ?", previousZone)
        }
    }

    @Test
    fun `turning notifications off removes only matching pending deliveries`() {
        val sent = publishEpisode(1)
        val pending = publishEpisode(2)
        jdbc.update("UPDATE push_delivery SET state = 'sent' WHERE id = ?", sent)
        outbox.notificationsDisabled(AnimeNotificationsDisabledEvent(account, anime + 1))
        assertEquals(2, countDeliveries())
        outbox.notificationsDisabled(AnimeNotificationsDisabledEvent(account, anime))
        assertEquals(1, countDeliveries())
        assertEquals("sent", state(sent))
        assertEquals(0, scalar("SELECT COUNT(*) FROM push_delivery WHERE id = ?", pending))
    }

    @Test
    fun `claim is exclusive and retries back off until the sixth attempt fails`() {
        val id = publishEpisode(1)
        var delivery = requireNotNull(outbox.claim(id))
        assertEquals(1, delivery.attempts)
        assertNull(outbox.claim(id))
        outbox.finish(id, "wrong-token", "sent")
        assertEquals("pending", state(id))
        for ((index, delay) in listOf(30, 120, 600, 3600, 21600).withIndex()) {
            assertEquals(index + 1, delivery.attempts)
            outbox.retry(delivery)
            val remaining = scalar("SELECT TIMESTAMPDIFF(SECOND, CURRENT_TIMESTAMP(6), next_attempt_at) FROM push_delivery WHERE id = ?", id)
            assertTrue(remaining in (delay - 2L)..delay.toLong())
            assertNull(outbox.claim(id))
            jdbc.update("UPDATE push_delivery SET next_attempt_at = DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 SECOND) WHERE id = ?", id)
            delivery = requireNotNull(outbox.claim(id))
        }
        assertEquals(6, delivery.attempts)
        outbox.retry(delivery)
        assertEquals("failed", state(id))
        assertNull(outbox.claim(id))
    }

    @Test
    fun `expired subscription response deletes only the matching client and cascades deliveries`() {
        val id = publishEpisode(1)
        val delivery = requireNotNull(outbox.claim(id))
        jdbc.update("UPDATE push_subscription SET client_id = ? WHERE id = ?", UUID.randomUUID().toString(), subscription)
        outbox.removeExpiredSubscription(delivery)
        assertEquals(1, scalar("SELECT COUNT(*) FROM push_subscription WHERE id = ?", subscription))
        jdbc.update("UPDATE push_subscription SET client_id = ? WHERE id = ?", clientId, subscription)
        outbox.removeExpiredSubscription(delivery)
        assertEquals(0, scalar("SELECT COUNT(*) FROM push_subscription WHERE id = ?", subscription))
        assertEquals(0, countDeliveries())
        assertEquals(1, countEvents())
    }

    @Test
    fun `delivery eligibility is rechecked after claim and expired subscriptions are excluded`() {
        val id = publishEpisode(1)
        val delivery = requireNotNull(outbox.claim(id))
        assertNotNull(outbox.loadEligible(id, delivery.token))
        jdbc.update("UPDATE anime_favorite SET notify = false WHERE an = ? AND anime_no = ?", account, anime)
        assertNull(outbox.loadEligible(id, delivery.token))
        jdbc.update("UPDATE anime_favorite SET notify = true WHERE an = ? AND anime_no = ?", account, anime)
        jdbc.update("UPDATE account SET ban_expire_dt = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY) WHERE an = ?", account)
        assertNull(outbox.loadEligible(id, delivery.token))
        jdbc.update("UPDATE account SET ban_expire_dt = NULL WHERE an = ?", account)
        jdbc.update("UPDATE anime_caption SET website = '' WHERE anime_no = ? AND an = ?", anime, account)
        assertNull(outbox.loadEligible(id, delivery.token))
        jdbc.update("UPDATE anime_caption SET website = 'https://caption.invalid/0', episode = '0' WHERE anime_no = ? AND an = ?", anime, account)
        assertNull(outbox.loadEligible(id, delivery.token))
        jdbc.update("UPDATE anime_caption SET episode = '1' WHERE anime_no = ? AND an = ?", anime, account)
        jdbc.update("UPDATE push_subscription SET expiration_time = ? WHERE id = ?", System.currentTimeMillis() - 1000, subscription)
        assertNull(outbox.loadEligible(id, delivery.token))
        publish("1", "https://caption.invalid/1", "2", "https://caption.invalid/2")
        assertEquals(2, countEvents())
        assertEquals(1, countDeliveries())
    }
}
