package anissia.wiki

import anissia.anime.domain.Anime
import jakarta.persistence.*
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.OffsetDateTime

@MappedSuperclass
abstract class WikiState {
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    var body: String = ""
    @Column(nullable = false)
    var revision: Long = 0
    @Column(nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
    @Column(nullable = false, length = 16)
    var updatedBy: String = ""
}

@Entity
@Table(name = "anime_wiki")
class AnimeWiki(@Id var animeNo: Long = 0) : WikiState() {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anime_no", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var anime: Anime? = null
}

@Entity
@Table(name = "wiki_template")
class WikiTemplate(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var templateNo: Long = 0,
    @Column(nullable = false, length = 100, unique = true)
    var title: String = "",
    @Column(nullable = false)
    var deleted: Boolean = false,
) : WikiState()

@Entity
@Table(name = "wiki_revision", uniqueConstraints = [UniqueConstraint(columnNames = ["kind", "target_no", "revision"])])
class WikiRevision(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(nullable = false, length = 16) var kind: String = "",
    @Column(nullable = false) var targetNo: Long = 0,
    @Column(nullable = false) var revision: Long = 0,
    @Column(nullable = false, length = 100) var title: String = "",
    @Column(nullable = false, columnDefinition = "LONGTEXT") var body: String = "",
    @Column(nullable = false, columnDefinition = "LONGTEXT") var templates: String = "[]",
    @Column(nullable = false, length = 300) var summary: String = "",
    @Column(nullable = false) var authorNo: Long = 0,
    @Column(nullable = false, length = 16) var updatedBy: String = "",
    @Column(nullable = false) var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)

@Entity
@Table(name = "wiki_reference", indexes = [Index(columnList = "target_kind,target_id"), Index(columnList = "owner_kind,owner_no")])
class WikiReference(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Column(nullable = false, length = 16) var ownerKind: String = "",
    @Column(nullable = false) var ownerNo: Long = 0,
    @Column(nullable = false, length = 16) var targetKind: String = "",
    @Column(nullable = false, length = 512) var targetId: String = "",
)
