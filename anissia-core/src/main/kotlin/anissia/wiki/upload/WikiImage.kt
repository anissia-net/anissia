package anissia.wiki.upload

import java.time.Instant

enum class WikiImageStatus { WAITING, PROCESSING, READY, FAILED, DELETING, DELETED, RESTORING }

data class WikiImage(
    val id: String,
    val ownerAn: Long?,
    val status: WikiImageStatus,
    val description: String,
    val sourceUrl: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attempts: Int = 0,
    val claimToken: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frames: Int? = null,
    val bytes: Long? = null,
    val animated: Boolean? = null,
    val error: String? = null,
    val revision: Long = 1,
    val updatedBy: String = "",
    val thumbnailBytes: Long? = null,
)

data class WikiImageDto(
    val id: String,
    val status: WikiImageStatus,
    val description: String,
    val sourceUrl: String,
    val createdAt: Instant,
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frames: Int? = null,
    val bytes: Long? = null,
    val animated: Boolean? = null,
    val error: String? = null,
    val revision: Long = 1,
    val updatedAt: Instant = createdAt,
    val updatedBy: String = "",
    val thumbnailUrl: String? = null,
)

data class WikiImageEditRequest(val baseRevision: Long = -1, val description: String = "", val sourceUrl: String = "")
data class WikiImageOperationRequest(val baseRevision: Long = -1)
data class WikiImageUsage(
    val kind: String,
    val targetNo: Long,
    val title: String,
    val url: String,
    val scope: String,
    val revision: Long? = null,
)
data class WikiImageUsagePage(
    val content: List<WikiImageUsage>,
    val totalElements: Long,
    val currentCount: Long,
    val historyCount: Long,
    val page: Int,
    val size: Int = 30,
    val canDelete: Boolean = false,
)

data class WikiImagePage(
    val content: List<WikiImageDto>,
    val totalElements: Long,
    val page: Int,
    val size: Int = 24,
)
