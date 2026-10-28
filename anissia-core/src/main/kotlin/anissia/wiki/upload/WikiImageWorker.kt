package anissia.wiki.upload

import anissia.account.repository.AccountRepository
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.logger
import anissia.wiki.WikiAccessService
import anissia.wiki.media.ImageConverter
import anissia.wiki.media.MediaException
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Component
class WikiImageWorker(
    private val repository: WikiImageRepository,
    private val storage: WikiImageStorage,
    private val converter: ImageConverter,
    private val properties: WikiUploadProperties,
    private val accounts: AccountRepository,
    private val access: WikiAccessService,
) {
    private val log = logger<WikiImageWorker>()
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "wiki-image-worker").apply { isDaemon = true } }
    private var lastCleanup = Instant.EPOCH

    @Scheduled(fixedDelay = 1_000)
    fun tick() {
        if (!properties.enabled || !running.compareAndSet(false, true)) return
        try {
            executor.submit {
                try {
                    storage.ensureDirectories()
                    repository.claim(properties.timeoutSeconds * 2 + 60)?.let(::process)
                    val now = Instant.now()
                    if (lastCleanup.plus(1, ChronoUnit.HOURS).isBefore(now)) {
                        storage.cleanup(now.minus(1, ChronoUnit.DAYS), repository::find)
                        lastCleanup = now
                    }
                } catch (e: Exception) {
                    log.error("wiki image worker failed", e)
                } finally {
                    running.set(false)
                }
            }
        } catch (e: RuntimeException) {
            running.set(false)
            if (!executor.isShutdown) throw e
        }
    }

    internal fun process(job: WikiImage) {
        val token = requireNotNull(job.claimToken)
        try {
            val owner = job.ownerAn?.let(accounts::findWithRolesByAn) ?: throw FailException("ACCOUNT_UNAVAILABLE")
            access.requireContributor(Actor.of(owner, ""))
            if (!Files.isRegularFile(storage.input(job.id), NOFOLLOW_LINKS)) throw IOException("missing private upload")
            val result = converter.convert(storage.input(job.id), storage.output(job.id, token))
            val thumbnail = converter.convertThumbnail(storage.input(job.id), storage.thumbnailOutput(job.id, token))
            access.requireContributor(Actor(an = owner.an))
            val completed = repository.complete(job.id, token, result, thumbnail.bytes) {
                storage.publish(job.id, token, result.bytes, thumbnail.bytes)
            }
            if (!completed) log.warn("wiki image claim expired: {}", job.id)
        } catch (e: Exception) {
            if (Thread.currentThread().isInterrupted) return
            val reason = when (e) {
                is MediaException -> e.reason.name
                is FailException -> "ACCOUNT_UNAVAILABLE"
                is IOException -> "STORAGE_FAILED"
                else -> "CONVERSION_FAILED"
            }
            log.warn("wiki image {} failed: {}", job.id, reason, e)
            runCatching { repository.fail(job.id, token, reason) }.onFailure { log.error("failed to record wiki image failure: {}", job.id, it) }
        } finally {
            // A commit may have succeeded even if the connection failed while acknowledging it.
            runCatching {
                val current = repository.find(job.id)
                if (current == null || current.status == WikiImageStatus.FAILED) storage.removeUnpublished(job.id)
                if (current == null || current.status == WikiImageStatus.FAILED || current.status == WikiImageStatus.READY) storage.removeTemporary(job.id)
            }.onFailure { log.warn("wiki image cleanup deferred: {}", job.id, it) }
        }
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }
}
