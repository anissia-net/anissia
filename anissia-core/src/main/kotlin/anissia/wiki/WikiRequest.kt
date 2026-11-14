package anissia.wiki

import anissia.wiki.upload.WikiImageDto
import jakarta.persistence.*
import java.time.OffsetDateTime

enum class WikiRequestKind { TEMPLATE_CREATE, TEMPLATE_DELETE, IMAGE_CREATE, IMAGE_DELETE }
enum class WikiRequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }

@Entity
@Table(name = "wiki_request", indexes = [
    Index(name = "wiki_request_idx_owner", columnList = "requester_an,id"),
    Index(name = "wiki_request_idx_status", columnList = "status,id"),
])
class WikiRequest(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long = 0,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) var kind: WikiRequestKind = WikiRequestKind.TEMPLATE_CREATE,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) var status: WikiRequestStatus = WikiRequestStatus.PENDING,
    @Column(nullable = false, length = 100) var title: String = "",
    @Column(columnDefinition = "LONGTEXT") var body: String? = null,
    @Column(nullable = false, length = 2000) var reason: String = "",
    var targetNo: Long? = null,
    @Column(length = 32) var imageId: String? = null,
    var baseRevision: Long? = null,
    @Column(nullable = false, length = 500) var description: String = "",
    @Column(nullable = false, length = 1024) var sourceUrl: String = "",
    @Column(nullable = false) var requesterAn: Long = 0,
    @Column(nullable = false, length = 16) var createdBy: String = "",
    @Column(nullable = false) var createdAt: OffsetDateTime = OffsetDateTime.now(),
    var processedAn: Long? = null,
    @Column(length = 16) var processedBy: String? = null,
    var processedAt: OffsetDateTime? = null,
    @Column(nullable = false, length = 2000) var processingReason: String = "",
    var resultTargetNo: Long? = null,
    @Column(length = 32) var resultImageId: String? = null,
)

data class WikiRequestSubmission(
    val kind: WikiRequestKind,
    val title: String = "",
    val body: WikiBody? = null,
    val reason: String = "",
    val targetNo: Long? = null,
    val imageId: String? = null,
    val baseRevision: Long? = null,
)

data class WikiRequestDecision(val reason: String = "")

data class WikiRequestDto(
    val id: Long,
    val kind: WikiRequestKind,
    val status: WikiRequestStatus,
    val title: String,
    val reason: String,
    val createdBy: String,
    val createdAt: OffsetDateTime,
    val processedBy: String?,
    val processedAt: OffsetDateTime?,
    val processingReason: String,
    val targetNo: Long?,
    val imageId: String?,
    val body: WikiBody?,
    val description: String,
    val sourceUrl: String,
    val imagePreviewUrl: String?,
    val resultTargetNo: Long?,
    val resultImageId: String?,
    val image: WikiImageDto?,
    val canCancel: Boolean,
    val canProcess: Boolean,
    val canApprove: Boolean,
)
