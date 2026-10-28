package anissia.wiki.upload

import anissia.support.FailException
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.time.Instant
import java.util.Comparator

@Component
class WikiImageStorage(private val properties: WikiUploadProperties) {
    private val tempRoot get() = Path.of(properties.tempRoot).toAbsolutePath().normalize()
    private val publicRoot get() = Path.of(properties.publicRoot).toAbsolutePath().normalize()
    private val originalRoot get() = Path.of(properties.originalRoot).toAbsolutePath().normalize()

    fun ensureDirectories() {
        require(properties.maxBytes in 1..(100L * 1024 * 1024))
        require(properties.maxQueueSize in 1..100 && properties.maxPendingPerAccount in 1..20)
        val publicUri = URI(properties.publicBaseUrl)
        require(publicUri.scheme in setOf("http", "https") && !publicUri.host.isNullOrBlank() &&
            publicUri.query == null && publicUri.fragment == null && publicUri.userInfo == null)
        Files.createDirectories(tempRoot)
        Files.createDirectories(publicRoot.resolve("wiki"))
        Files.createDirectories(originalRoot)
        val roots = listOf(tempRoot, publicRoot, originalRoot).map { it.toRealPath() }
        require(roots.toSet().size == 3 && roots.none { a -> roots.any { b -> a != b && a.startsWith(b) } }) {
            "업로드 임시·공개·원본 디렉터리는 서로 분리해야 합니다."
        }
    }

    fun input(id: String): Path = jobDirectory(id).resolve("source")
    fun output(id: String, token: String): Path = jobDirectory(id).resolve("${validId(token)}.avif")
    fun thumbnailOutput(id: String, token: String): Path = jobDirectory(id).resolve("${validId(token)}.thumb.avif")
    fun publicFile(id: String): Path = publicRoot.resolve("wiki").resolve("${validId(id)}.avif")
    fun thumbnailFile(id: String): Path = publicRoot.resolve("wiki").resolve("${validId(id)}.thumb.avif")
    fun thumbnailUrl(id: String): String = "${properties.publicBaseUrl.trimEnd('/')}/wiki/${validId(id)}.thumb.avif"
    fun archivedThumbnailFile(id: String): Path = originalRoot.resolve("archive").resolve("${validId(id)}.thumb.avif")
    fun publicUrl(id: String): String = "${properties.publicBaseUrl.trimEnd('/')}/wiki/${validId(id)}.avif"
    fun archivedFile(id: String): Path = originalRoot.resolve("archive").resolve("${validId(id)}.avif")

    fun archive(id: String, expectedBytes: Long, thumbnailBytes: Long? = null) {
        ensureDirectories()
        Files.createDirectories(originalRoot.resolve("archive"))
        moveComplete(publicFile(id), archivedFile(id), expectedBytes)
        thumbnailBytes?.let { moveComplete(thumbnailFile(id), archivedThumbnailFile(id), it) }
    }

    fun restore(id: String, expectedBytes: Long, thumbnailBytes: Long? = null) {
        ensureDirectories()
        thumbnailBytes?.let { moveComplete(archivedThumbnailFile(id), thumbnailFile(id), it) }
        moveComplete(archivedFile(id), publicFile(id), expectedBytes)
    }

    private fun moveComplete(source: Path, target: Path, expectedBytes: Long) {
        if (expectedBytes <= 0) throw IOException("missing image size")
        val hasSource = Files.exists(source, NOFOLLOW_LINKS)
        val hasTarget = Files.exists(target, NOFOLLOW_LINKS)
        if (hasSource && hasTarget) throw IOException("both public and private image copies exist")
        val existing = if (hasSource) source else target
        if (!Files.isRegularFile(existing, NOFOLLOW_LINKS) || Files.size(existing) != expectedBytes) {
            throw IOException("complete image file is unavailable")
        }
        if (hasSource) Files.move(source, target, ATOMIC_MOVE)
    }

    fun receive(id: String, source: InputStream) {
        ensureDirectories()
        Files.createDirectory(jobDirectory(id))
        try {
            Files.newOutputStream(input(id), CREATE_NEW).use { target ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > properties.maxBytes) throw FailException("이미지는 최대 ${properties.maxBytes / (1024 * 1024)}MB까지 업로드할 수 있습니다.")
                    target.write(buffer, 0, count)
                }
                if (total == 0L) throw FailException("빈 파일은 업로드할 수 없습니다.")
            }
        } catch (e: Exception) {
            removeTemporary(id)
            throw e
        }
    }

    fun publish(id: String, token: String, expectedBytes: Long, thumbnailBytes: Long) {
        val converted = output(id, token)
        check(Files.isRegularFile(converted, NOFOLLOW_LINKS) && expectedBytes > 0 && Files.size(converted) == expectedBytes)
        val thumbnail = thumbnailOutput(id, token)
        check(Files.isRegularFile(thumbnail, NOFOLLOW_LINKS) && thumbnailBytes > 0 && Files.size(thumbnail) == thumbnailBytes)
        if (properties.retainOriginals) {
            val staged = originalRoot.resolve("${validId(id)}.${validId(token)}.tmp")
            try {
                Files.copy(input(id), staged, REPLACE_EXISTING)
                Files.move(staged, originalRoot.resolve("${validId(id)}.source"), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(staged)
            }
        }
        // Cross-filesystem configurations fail here instead of exposing a partially copied image.
        Files.move(thumbnail, thumbnailFile(id), ATOMIC_MOVE, REPLACE_EXISTING)
        Files.move(converted, publicFile(id), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    fun removeTemporary(id: String) = removeTree(jobDirectory(id))
    fun removeUnpublished(id: String) {
        Files.deleteIfExists(publicFile(id))
        Files.deleteIfExists(thumbnailFile(id))
        Files.deleteIfExists(originalRoot.resolve("${validId(id)}.source"))
    }

    fun cleanup(before: Instant, lookup: (String) -> WikiImage?) {
        if (Files.isDirectory(tempRoot)) Files.list(tempRoot).use { entries ->
            entries.filter { ID.matches(it.fileName.toString()) && Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toInstant().isBefore(before) }
                .forEach {
                    val record = lookup(it.fileName.toString())
                    if (record == null || record.status == WikiImageStatus.READY || record.status == WikiImageStatus.FAILED) removeTree(it)
                }
        }
        for ((root, extension) in listOf(publicRoot.resolve("wiki") to ".avif", publicRoot.resolve("wiki") to ".thumb.avif", originalRoot to ".source")) {
            if (!Files.isDirectory(root)) continue
            Files.list(root).use { entries ->
                entries.filter { it.fileName.toString().endsWith(extension) && ID.matches(it.fileName.toString().removeSuffix(extension)) }
                    .filter { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toInstant().isBefore(before) }
                    .forEach {
                        val record = lookup(it.fileName.toString().removeSuffix(extension))
                        if (record == null || record.status == WikiImageStatus.FAILED) Files.deleteIfExists(it)
                    }
            }
        }
        if (Files.isDirectory(originalRoot)) Files.list(originalRoot).use { entries ->
            entries.filter { STAGED_ORIGINAL.matches(it.fileName.toString()) && Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toInstant().isBefore(before) }
                .forEach {
                    val record = lookup(it.fileName.toString().substringBefore('.'))
                    if (record == null || record.status == WikiImageStatus.READY || record.status == WikiImageStatus.FAILED) Files.deleteIfExists(it)
                }
        }
    }

    private fun jobDirectory(id: String) = tempRoot.resolve(validId(id))
    private fun removeTree(directory: Path) {
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return
        Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
    }

    companion object {
        val ID = Regex("[0-9a-f]{32}")
        private val STAGED_ORIGINAL = Regex("[0-9a-f]{32}\\.[0-9a-f]{32}\\.tmp")
        fun validId(id: String): String = id.also { require(ID.matches(it)) { "잘못된 이미지 번호입니다." } }
    }
}
