package anissia.wiki.upload

import anissia.support.FailException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
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
}
