package anissia.push

import anissia.account.domain.Account
import anissia.anime.domain.Anime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.OffsetDateTime

/** 스키마는 JPA 개발환경과 운영 SQL 마이그레이션이 동일하다. 큐의 조건부 갱신은 JDBC로 수행한다. */
@Entity
@Table(
    name = "push_subscription",
    uniqueConstraints = [UniqueConstraint(name = "push_subscription_uk_endpoint", columnNames = ["endpoint_hash"])],
    indexes = [Index(name = "push_subscription_idx_an", columnList = "an")],
)
class PushSubscription(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false) var an: Long = 0,
    @Column(name = "endpoint_hash", nullable = false, length = 64) var endpointHash: String = "",
    @Column(nullable = false, length = 2048) var endpoint: String = "",
    @Column(nullable = false, length = 128) var p256dh: String = "",
    @Column(nullable = false, length = 128) var auth: String = "",
    @Column(name = "client_id", nullable = false, length = 36) var clientId: String = "",
    @Column(name = "expiration_time") var expirationTime: Long? = null,
    @Column(name = "created_at", nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: OffsetDateTime = OffsetDateTime.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "an", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "push_subscription_fk_account"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var account: Account? = null,
)

@Entity
@Table(name = "caption_push_event")
class CaptionPushEvent(
    @Id @Column(name = "event_key", length = 64) var eventKey: String = "",
    @Column(name = "anime_no", nullable = false) var animeNo: Long = 0,
    @Column(name = "translator_an", nullable = false) var translatorAn: Long = 0,
    @Column(nullable = false, length = 10) var episode: String = "",
    @Column(nullable = false, length = 100) var subject: String = "",
    @Column(name = "translator_name", nullable = false, length = 100) var translatorName: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anime_no", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "caption_push_event_fk_anime"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var anime: Anime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "translator_an", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "caption_push_event_fk_translator"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var translator: Account? = null,
)

@Entity
@Table(
    name = "push_delivery",
    uniqueConstraints = [UniqueConstraint(name = "push_delivery_uk_event_subscription", columnNames = ["event_key", "subscription_id"])],
    indexes = [Index(name = "push_delivery_idx_due", columnList = "state,next_attempt_at,id")],
)
class PushDelivery(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(name = "event_key", nullable = false, length = 64) var eventKey: String = "",
    @Column(name = "subscription_id", nullable = false) var subscriptionId: Long = 0,
    @Column(nullable = false, length = 16) var state: String = "pending",
    @Column(nullable = false) var attempts: Int = 0,
    @Column(name = "claim_token", length = 36) var claimToken: String? = null,
    @Column(name = "next_attempt_at", nullable = false) var nextAttemptAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "created_at", nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "finished_at") var finishedAt: OffsetDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_key", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "push_delivery_fk_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var event: CaptionPushEvent? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "push_delivery_fk_subscription"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var subscription: PushSubscription? = null,
)
