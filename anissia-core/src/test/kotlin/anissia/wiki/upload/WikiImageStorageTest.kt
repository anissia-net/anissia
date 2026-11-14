package anissia.wiki.upload

import anissia.support.FailException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.io.IOException
import java.time.Instant

class WikiImageStorageTest {
    @TempDir lateinit var root: Path
    private val id = "a".repeat(32)
    private val token = "b".repeat(32)
    private fun properties() = WikiUploadProperties(
        tempRoot = root.resolve("temp").toString(), publicRoot = root.resolve("public").toString(),
        originalRoot = root.resolve("originals").toString(), maxBytes = 8,
    )

    @Test
    fun `stream limit removes partially received bytes without exposing a public file`() {
        val storage = WikiImageStorage(properties())
        assertThrows<FailException> { storage.receive(id, ByteArray(9).inputStream()) }
        assertFalse(Files.exists(storage.input(id)))
        assertFalse(Files.exists(storage.publicFile(id)))
    }

    @Test
    fun `private originals are retained outside public root and only complete output is published`() {
        val config = properties().apply { retainOriginals = true }
        val storage = WikiImageStorage(config)
        val source = byteArrayOf(1, 2, 3)
        val output = byteArrayOf(4, 5)
        storage.receive(id, source.inputStream())
        Files.write(storage.output(id, token), output)
        Files.write(storage.thumbnailOutput(id, token), byteArrayOf(6))
        storage.publish(id, token, 2, 1)
        storage.removeTemporary(id)
        assertArrayEquals(output, Files.readAllBytes(storage.publicFile(id)))
        assertArrayEquals(byteArrayOf(6), Files.readAllBytes(storage.thumbnailFile(id)))
        assertArrayEquals(source, Files.readAllBytes(root.resolve("originals/$id.source")))
        assertFalse(Files.exists(storage.input(id)))
        assertFalse(Files.exists(root.resolve("public/$id.source")))
    }

    @Test
    fun `private converter outputs become readable by the public web server`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val storage = WikiImageStorage(properties())
        storage.receive(id, byteArrayOf(1).inputStream())
        for (path in listOf(storage.output(id, token), storage.thumbnailOutput(id, token))) {
            Files.write(path, byteArrayOf(2))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        }
        storage.publish(id, token, 1, 1)
        for (path in listOf(storage.publicFile(id), storage.thumbnailFile(id))) {
            assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(path))
        }
    }

    @Test
    fun `restoring private legacy files repairs public permissions including retries`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1))
        Files.write(storage.thumbnailFile(id), byteArrayOf(2))
        storage.archive(id, 1, 1)
        for (path in listOf(storage.archivedFile(id), storage.archivedThumbnailFile(id))) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        }
        Files.move(storage.archivedThumbnailFile(id), storage.thumbnailFile(id))
        storage.restore(id, 1, 1)
        storage.restore(id, 1, 1)
        for (path in listOf(storage.publicFile(id), storage.thumbnailFile(id))) {
            assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(path))
        }
    }

    @Test
    fun `public directory is searchable even when created with a restrictive umask`() {
        assumeTrue(root.fileSystem.supportedFileAttributeViews().contains("posix"))
        val directory = Files.createDirectories(root.resolve("public/wiki"))
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
        WikiImageStorage(properties()).ensureDirectories()
        assertEquals(PosixFilePermissions.fromString("rwxr-xr-x"), Files.getPosixFilePermissions(directory))
    }

    @Test
    fun `incomplete output is never published`() {
        val storage = WikiImageStorage(properties())
        storage.receive(id, byteArrayOf(1).inputStream())
        Files.write(storage.output(id, token), byteArrayOf(2))
        assertThrows<IllegalStateException> { storage.publish(id, token, 2, 1) }
        assertFalse(Files.exists(storage.publicFile(id)))
    }

    @Test
    fun `missing thumbnail prevents publication of either image`() {
        val storage = WikiImageStorage(properties())
        storage.receive(id, byteArrayOf(1).inputStream())
        Files.write(storage.output(id, token), byteArrayOf(2))
        assertThrows<IllegalStateException> { storage.publish(id, token, 1, 1) }
        assertFalse(Files.exists(storage.publicFile(id)))
        assertFalse(Files.exists(storage.thumbnailFile(id)))
    }

    @Test
    fun `archive and restore recover a partially moved image pair`() {
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1, 2))
        Files.write(storage.thumbnailFile(id), byteArrayOf(3))
        storage.archive(id, 2)
        storage.archive(id, 2, 1)
        storage.archive(id, 2, 1)
        assertFalse(Files.exists(storage.publicFile(id)))
        assertFalse(Files.exists(storage.thumbnailFile(id)))
        Files.move(storage.archivedThumbnailFile(id), storage.thumbnailFile(id))
        storage.restore(id, 2, 1)
        storage.restore(id, 2, 1)
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(storage.publicFile(id)))
        assertArrayEquals(byteArrayOf(3), Files.readAllBytes(storage.thumbnailFile(id)))
        assertFalse(Files.exists(storage.archivedFile(id)))
        assertFalse(Files.exists(storage.archivedThumbnailFile(id)))
    }

    @Test
    fun `failed publication cleanup removes both variants`() {
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1, 2))
        Files.write(storage.thumbnailFile(id), byteArrayOf(3))
        storage.removeUnpublished(id)
        assertFalse(Files.exists(storage.publicFile(id)))
        assertFalse(Files.exists(storage.thumbnailFile(id)))
    }

    @Test
    fun `replacement preserves private previous bytes and is safe to retry before database acknowledgement`() {
        val config = properties().apply { retainOriginals = true }
        val storage = WikiImageStorage(config)
        storage.receive(id, byteArrayOf(1).inputStream())
        Files.write(storage.output(id, token), byteArrayOf(2))
        Files.write(storage.thumbnailOutput(id, token), byteArrayOf(3))
        storage.publish(id, token, 1, 1)
        storage.removeTemporary(id)
        val replacement = "c".repeat(32)
        storage.receive(replacement, byteArrayOf(4).inputStream())
        Files.write(storage.output(replacement, token), byteArrayOf(5, 6))
        Files.write(storage.thumbnailOutput(replacement, token), byteArrayOf(7))
        val original = WikiImage(id, 7, WikiImageStatus.READY, "이전", "", Instant.EPOCH, Instant.EPOCH, bytes = 1, thumbnailBytes = 1)
        val job = WikiImage(replacement, 8, WikiImageStatus.REPLACING, "새 설명", "", Instant.EPOCH, Instant.EPOCH,
            claimToken = token, bytes = 2, thumbnailBytes = 1, submissionKind = "REPLACEMENT", replacementFor = id)
        repeat(2) { storage.replace(job, original) }
        assertArrayEquals(byteArrayOf(5, 6), Files.readAllBytes(storage.publicFile(id)))
        assertArrayEquals(byteArrayOf(7), Files.readAllBytes(storage.thumbnailFile(id)))
        assertArrayEquals(byteArrayOf(4), Files.readAllBytes(root.resolve("originals/$id.source")))
        val backup = root.resolve("originals/replacements/$id/$replacement")
        assertArrayEquals(byteArrayOf(2), Files.readAllBytes(backup.resolve("image.avif")))
        assertArrayEquals(byteArrayOf(3), Files.readAllBytes(backup.resolve("thumbnail.avif")))
        assertArrayEquals(byteArrayOf(1), Files.readAllBytes(backup.resolve("source")))
        assertFalse(Files.exists(storage.publicFile(replacement)))
        assertTrue(Files.exists(storage.output(replacement, token)))
    }

    @Test
    fun `request identifiers cannot escape storage roots`() {
        val storage = WikiImageStorage(properties())
        assertThrows<IllegalArgumentException> { storage.input("../../secret") }
        assertThrows<IllegalArgumentException> { storage.output(id, "../bad") }
        assertThrows<IllegalArgumentException> { storage.publicFile("a".repeat(31)) }
    }

    @Test
    fun `public root cannot contain original or temporary directories`() {
        val config = properties().apply { originalRoot = root.resolve("public/originals").toString() }
        assertThrows<IllegalArgumentException> { WikiImageStorage(config).ensureDirectories() }
    }

    @Test
    fun `stale cleanup preserves interrupted jobs but removes abandoned uploads`() {
        val storage = WikiImageStorage(properties())
        val abandoned = "c".repeat(32)
        storage.receive(id, byteArrayOf(1).inputStream())
        storage.receive(abandoned, byteArrayOf(2).inputStream())
        val old = Instant.now().minusSeconds(172_800)
        Files.setLastModifiedTime(storage.input(id).parent, FileTime.from(old))
        Files.setLastModifiedTime(storage.input(abandoned).parent, FileTime.from(old))
        val processing = WikiImage(id, 7, WikiImageStatus.PROCESSING, "", "", old, old)
        storage.cleanup(Instant.now().minusSeconds(86_400)) { if (it == id) processing else null }
        assertTrue(Files.exists(storage.input(id)))
        assertFalse(Files.exists(storage.input(abandoned)))
    }

    @Test
    fun `archive and restore remove public exposure and safely repeat after a restart`() {
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1, 2))
        storage.archive(id, 2)
        assertFalse(Files.exists(storage.publicFile(id)))
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(storage.archivedFile(id)))
        storage.archive(id, 2)
        storage.restore(id, 2)
        storage.restore(id, 2)
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(storage.publicFile(id)))
        assertFalse(Files.exists(storage.archivedFile(id)))
    }

    @Test
    fun `archive recovery never overwrites conflicting copies or accepts incomplete bytes`() {
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1, 2))
        assertThrows<IOException> { storage.archive(id, 3) }
        storage.archive(id, 2)
        Files.write(storage.publicFile(id), byteArrayOf(3, 4))
        assertThrows<IOException> { storage.restore(id, 2) }
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(storage.archivedFile(id)))
        assertArrayEquals(byteArrayOf(3, 4), Files.readAllBytes(storage.publicFile(id)))
    }

    @Test
    fun `attachment copies public variants and private original independently`() {
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1, 2))
        Files.write(storage.thumbnailFile(id), byteArrayOf(3))
        Files.write(root.resolve("originals/$id.source"), byteArrayOf(4))
        val target = "c".repeat(32)
        val source = WikiImage(id, 7, WikiImageStatus.READY, "사진", "", Instant.EPOCH, Instant.EPOCH, bytes = 2, thumbnailBytes = 1)
        storage.copy(source, target)
        Files.write(storage.publicFile(target), byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(storage.publicFile(id)))
        assertArrayEquals(byteArrayOf(3), Files.readAllBytes(storage.thumbnailFile(target)))
        assertArrayEquals(byteArrayOf(4), Files.readAllBytes(root.resolve("originals/$target.source")))
        assertFalse(Files.exists(root.resolve("public/$target.source")))
    }

    @Test
    fun `incomplete attachment copy removes copied output and preserves source`() {
        val storage = WikiImageStorage(properties())
        storage.ensureDirectories()
        Files.write(storage.publicFile(id), byteArrayOf(1, 2))
        val target = "c".repeat(32)
        val source = WikiImage(id, 7, WikiImageStatus.READY, "사진", "", Instant.EPOCH, Instant.EPOCH, bytes = 2, thumbnailBytes = 1)
        assertThrows<IOException> { storage.copy(source, target) }
        assertFalse(Files.exists(storage.publicFile(target)))
        assertFalse(Files.exists(storage.thumbnailFile(target)))
        assertArrayEquals(byteArrayOf(1, 2), Files.readAllBytes(storage.publicFile(id)))
    }
}
