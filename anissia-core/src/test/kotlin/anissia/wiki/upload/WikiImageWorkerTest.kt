package anissia.wiki.upload

import anissia.account.domain.Account
import anissia.account.repository.AccountRepository
import anissia.security.Actor
import anissia.support.FailException
import anissia.wiki.WikiAccessService
import anissia.wiki.media.ConvertedImage
import anissia.wiki.media.ImageConverter
import anissia.wiki.media.MediaException
import anissia.wiki.media.MediaFailure
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class WikiImageWorkerTest {
    @TempDir lateinit var root: Path
    private val id = "a".repeat(32)
    private val token = "b".repeat(32)
    private val repository = mock(WikiImageRepository::class.java)
    private val accounts = mock(AccountRepository::class.java)
    private val access = mock(WikiAccessService::class.java)
    private val owner = Account(an = 7)
    private val job = WikiImage(id, 7, WikiImageStatus.PROCESSING, "", "", Instant.EPOCH, Instant.EPOCH, claimToken = token)

    private fun config() = WikiUploadProperties(tempRoot = root.resolve("temp").toString(),
        publicRoot = root.resolve("public").toString(), originalRoot = root.resolve("original").toString())

    private fun allow() {
        `when`(accounts.findWithRolesByAn(owner.an)).thenReturn(owner)
        `when`(access.requireContributor(any(Actor::class.java) ?: Actor())).thenReturn(owner)
    }

    @Test
    fun `conversion failure removes private input and never exposes output`() {
        val properties = config()
        val storage = WikiImageStorage(properties)
        storage.receive(id, byteArrayOf(1).inputStream())
        allow()
        val converter = object : ImageConverter() {
            override fun convert(input: Path, output: Path): ConvertedImage = throw MediaException(MediaFailure.INVALID_IMAGE, "invalid")
        }
        `when`(repository.find(id)).thenReturn(job.copy(status = WikiImageStatus.FAILED))
        val worker = WikiImageWorker(repository, storage, converter, properties, accounts, access)
        try { worker.process(job) } finally { worker.stop() }
        verify(repository).fail(id, token, "INVALID_IMAGE")
        assertFalse(Files.exists(storage.input(id)))
        assertFalse(Files.exists(storage.publicFile(id)))
    }

    @Test
    fun `thumbnail conversion failure prevents publication of the main image`() {
        val properties = config()
        val storage = WikiImageStorage(properties)
        storage.receive(id, byteArrayOf(1).inputStream())
        allow()
        val converter = object : ImageConverter() {
            override fun convert(input: Path, output: Path): ConvertedImage {
                Files.write(output, byteArrayOf(2))
                return ConvertedImage(1, 1, 1, 0, false, false, 1)
            }
            override fun convertThumbnail(input: Path, output: Path): ConvertedImage =
                throw MediaException(MediaFailure.CONVERSION_FAILED, "thumbnail failed")
        }
        `when`(repository.find(id)).thenReturn(job.copy(status = WikiImageStatus.FAILED))
        val worker = WikiImageWorker(repository, storage, converter, properties, accounts, access)
        try { worker.process(job) } finally { worker.stop() }
        verify(repository).fail(id, token, "CONVERSION_FAILED")
        assertFalse(Files.exists(storage.input(id)))
        assertFalse(Files.exists(storage.publicFile(id)))
        assertFalse(Files.exists(storage.thumbnailFile(id)))
    }

    @Test
    fun `account blocked while converting cannot publish the completed image`() {
        val properties = config()
        val storage = WikiImageStorage(properties)
        storage.receive(id, byteArrayOf(1).inputStream())
        `when`(accounts.findWithRolesByAn(owner.an)).thenReturn(owner)
        `when`(access.requireContributor(any(Actor::class.java) ?: Actor()))
            .thenReturn(owner).thenThrow(FailException("blocked"))
        val converter = object : ImageConverter() {
            override fun convert(input: Path, output: Path): ConvertedImage {
                Files.write(output, byteArrayOf(2))
                return ConvertedImage(1, 1, 1, 0, false, false, 1)
            }
            override fun convertThumbnail(input: Path, output: Path): ConvertedImage {
                Files.write(output, byteArrayOf(3))
                return ConvertedImage(64, 64, 1, 0, false, false, 1)
            }
        }
        `when`(repository.find(id)).thenReturn(job.copy(status = WikiImageStatus.FAILED))
        val worker = WikiImageWorker(repository, storage, converter, properties, accounts, access)
        try { worker.process(job) } finally { worker.stop() }
        verify(repository).fail(id, token, "ACCOUNT_UNAVAILABLE")
        assertFalse(Files.exists(storage.publicFile(id)))
        assertFalse(Files.exists(storage.input(id)))
    }

    @Test
    fun `lost commit acknowledgement cannot delete a successfully published file`() {
        val properties = config()
        val storage = WikiImageStorage(properties)
        storage.receive(id, byteArrayOf(1).inputStream())
        allow()
        val converter = object : ImageConverter() {
            override fun convert(input: Path, output: Path): ConvertedImage {
                Files.write(output, byteArrayOf(2))
                return ConvertedImage(1, 1, 1, 0, false, false, 1)
            }
            override fun convertThumbnail(input: Path, output: Path): ConvertedImage {
                Files.write(output, byteArrayOf(3))
                return ConvertedImage(64, 64, 1, 0, false, false, 1)
            }
        }
        doAnswer { invocation ->
            invocation.getArgument<() -> Unit>(4).invoke()
            throw IOException("commit acknowledgement lost")
        }.`when`(repository).complete(anyString(), anyString(),
            any(ConvertedImage::class.java) ?: ConvertedImage(1, 1, 1, 0, false, false, 1),
            anyLong(), any<() -> Unit>() ?: {})
        `when`(repository.find(id)).thenReturn(job.copy(status = WikiImageStatus.READY))
        val worker = WikiImageWorker(repository, storage, converter, properties, accounts, access)
        try { worker.process(job) } finally { worker.stop() }
        assertTrue(Files.exists(storage.publicFile(id)))
        assertTrue(Files.exists(storage.thumbnailFile(id)))
        assertFalse(Files.exists(storage.input(id)))
    }
}
