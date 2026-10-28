package anissia.notification.domain

import anissia.account.domain.Account
import anissia.anime.domain.Anime
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.io.Serializable
import java.time.OffsetDateTime

@Entity
// 기존 데이터와 외래 키를 유지하기 위해 물리 테이블 이름은 바꾸지 않는다.
@Table(
    name = "anime_favorite",
    indexes = [Index(name = "anime_favorite_idx__anime_notify", columnList = "anime_no,notify,an")],
)
@IdClass(AnimeNotification.Key::class)
class AnimeNotification(
    @Id
    @Column(name = "an", nullable = false)
    var an: Long = 0,

    @Id
    @Column(name = "anime_no", nullable = false)
    var animeNo: Long = 0,

    @Column(nullable = false)
    var notify: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "an",
        foreignKey = ForeignKey(name = "anime_favorite_fk_account"),
        nullable = false,
        insertable = false,
        updatable = false,
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    var account: Account? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "anime_no",
        foreignKey = ForeignKey(name = "anime_favorite_fk_anime"),
        nullable = false,
        insertable = false,
        updatable = false,
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    var anime: Anime? = null,
) {
    data class Key(val an: Long = 0, val animeNo: Long = 0) : Serializable
}
