package anissia.notification.domain

import anissia.account.domain.Account
import anissia.push.CaptionPushEvent
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
import java.time.LocalDateTime

@Entity
@Table(
    name = "notification_inbox",
    uniqueConstraints = [UniqueConstraint(name = "notification_inbox_uk_account_event", columnNames = ["an", "event_key"])],
    indexes = [
        Index(name = "notification_inbox_idx_account_recent", columnList = "an,id"),
        Index(name = "notification_inbox_idx_account_unread", columnList = "an,read_at"),
    ],
)
class NotificationInboxEntry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(nullable = false) var an: Long = 0,
    @Column(name = "event_key", nullable = false, length = 64) var eventKey: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "read_at") var readAt: LocalDateTime? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "an", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "notification_inbox_fk_account"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var account: Account? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_key", insertable = false, updatable = false, nullable = false,
        foreignKey = ForeignKey(name = "notification_inbox_fk_event"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var event: CaptionPushEvent? = null,
)
