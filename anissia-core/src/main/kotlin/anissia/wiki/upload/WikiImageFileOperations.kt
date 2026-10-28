package anissia.wiki.upload

import anissia.support.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class WikiImageFileOperations(
    private val repository: WikiImageRepository,
    private val storage: WikiImageStorage,
    private val properties: WikiUploadProperties,
) {
    private val log = logger<WikiImageFileOperations>()

    fun process(id: String) {
        try {
            repository.applyFileOperation(id) { image ->
                val bytes = requireNotNull(image.bytes)
                if (image.status == WikiImageStatus.DELETING) storage.archive(id, bytes, image.thumbnailBytes)
                else storage.restore(id, bytes, image.thumbnailBytes)
            }
        } catch (e: Exception) {
            log.warn("wiki image archive operation deferred: {}", id, e)
            runCatching { repository.fileOperationFailed(id) }
                .onFailure { log.error("could not persist wiki archive retry: {}", id, it) }
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    fun recover() {
        if (!properties.enabled) return
        try {
            repository.dueFileOperations().forEach(::process)
        } catch (e: Exception) {
            log.warn("wiki image archive recovery deferred", e)
        }
    }
}
