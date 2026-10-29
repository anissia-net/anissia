package anissia.wiki.upload

import anissia.account.domain.Account
import anissia.activepanel.service.ActivePanelLogService
import anissia.security.Actor
import anissia.support.FailException
import anissia.wiki.WikiAccessService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.inOrder
import org.springframework.mock.web.MockMultipartFile
import java.time.Instant
import java.nio.file.Path
import org.springframework.web.server.ResponseStatusException

class WikiImageServiceTest {
    private val repository = mock(WikiImageRepository::class.java)
    private val storage = mock(WikiImageStorage::class.java)
    private val access = mock(WikiAccessService::class.java)
    private val properties = WikiUploadProperties(enabled = true, publicBaseUrl = "https://file.anissia.net/file")
    private val operations = mock(WikiImageFileOperations::class.java)
    private val logs = mock(ActivePanelLogService::class.java)
    private val service = WikiImageService(repository, storage, access, properties, operations, logs, org.mockito.Mockito.mock(anissia.search.ContentSearchRepository::class.java))
    private val id = "a".repeat(32)
    private val actor = Actor(an = 7)
    private val url = "${properties.publicBaseUrl}/wiki/$id.avif"
    private fun record(status: WikiImageStatus) = WikiImage(id, actor.an, status, "설명", "", Instant.EPOCH, Instant.EPOCH)

    @Test
    fun `age and ban checks happen before receiving bytes or creating jobs`() {
        `when`(access.requireContributor(actor)).thenThrow(FailException("가입 후 15일"))
        assertThrows<FailException> {
            service.upload(MockMultipartFile("file", byteArrayOf(1)), "", "", actor)
        }
        verifyNoInteractions(repository, storage)
    }

    @Test
    fun `pending image status is private to current contributor or operator`() {
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.PROCESSING))
        val stranger = Actor(an = 8)
        `when`(access.requireOperator(stranger)).thenThrow(FailException("권한 없음"))
        assertThrows<FailException> { service.get(id, stranger) }
        verify(access).requireOperator(stranger)
        `when`(access.requireContributor(actor)).thenReturn(Account(an = actor.an))
        assertEquals(WikiImageStatus.PROCESSING, service.get(id, actor).status)
        verify(access).requireContributor(actor)
    }

    @Test
    fun `ready images are public and do not expose uploader account`() {
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.READY))
        `when`(storage.publicUrl(id)).thenReturn(url)
        assertEquals(url, service.get(id, Actor()).url)
        verifyNoInteractions(access)
    }

    @Test
    fun `only exact published image urls can be referenced`() {
        `when`(storage.publicUrl(id)).thenReturn(url)
        `when`(repository.lock(id)).thenReturn(record(WikiImageStatus.READY))
        service.requireReadyUrls(setOf(url))
        for (invalid in listOf("https://evil.example/wiki/$id.avif", "$url?x=1", "${properties.publicBaseUrl}/wiki/../$id.avif")) {
            assertThrows<FailException> { service.requireReadyUrls(setOf(invalid)) }
        }
        `when`(repository.lock(id)).thenReturn(record(WikiImageStatus.PROCESSING))
        assertThrows<FailException> { service.requireReadyUrls(setOf(url)) }
    }

    @Test
    fun `metadata cannot introduce active urls or credentials`() {
        assertTrue(WikiImageService.validSource("https://example.com/image?a=1"))
        assertTrue(WikiImageService.validSource(""))
        assertFalse(WikiImageService.validSource("javascript:alert(1)"))
        assertFalse(WikiImageService.validSource("https://user:password@example.com/image"))
        `when`(access.requireContributor(actor)).thenReturn(Account(an = actor.an))
        assertThrows<IllegalArgumentException> {
            service.upload(MockMultipartFile("file", byteArrayOf(1)), "a".repeat(501), "", actor)
        }
        verifyNoInteractions(repository, storage)
    }

    @Test
    fun `disabled upload does not call policy or touch storage`() {
        properties.enabled = false
        assertThrows<FailException> { service.upload(MockMultipartFile("file", byteArrayOf(1)), "", "", actor) }
        verifyNoInteractions(access, storage, repository)
    }

    @Test
    fun `uploader cannot read archived images or deleted list without operator permission`() {
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.DELETED))
        `when`(access.requireOperator(actor)).thenThrow(FailException("운영자만 가능"))
        assertThrows<FailException> { service.get(id, actor) }
        assertThrows<FailException> { service.list(0, "", true, actor) }
    }

    @Test
    fun `metadata writes enforce current contributor policy before touching records`() {
        `when`(access.requireContributor(actor)).thenThrow(FailException("차단됨"))
        assertThrows<FailException> { service.edit(id, WikiImageEditRequest(1, "changed", ""), actor) }
        verifyNoInteractions(repository, operations, logs)
    }

    @Test
    fun `image removal and restoration require operator before file operations`() {
        `when`(access.requireOperator(actor)).thenThrow(FailException("운영자만 가능"))
        assertThrows<FailException> { service.delete(id, WikiImageOperationRequest(1), actor) }
        assertThrows<FailException> { service.restore(id, WikiImageOperationRequest(1), actor) }
        verifyNoInteractions(repository, operations, logs)
    }

    @Test
    fun `thumbnail url and file are exposed only for ready generated thumbnails`() {
        properties.serveFiles = true
        val thumb = "${properties.publicBaseUrl}/wiki/$id.thumb.avif"
        `when`(storage.thumbnailUrl(id)).thenReturn(thumb)
        `when`(storage.thumbnailFile(id)).thenReturn(Path.of("/tmp/$id.thumb.avif"))
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.READY))
        assertNull(service.get(id, Actor()).thumbnailUrl)
        assertThrows<ResponseStatusException> { service.readyThumbnailFile(id) }
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.READY).copy(thumbnailBytes = 10))
        assertEquals(thumb, service.get(id, Actor()).thumbnailUrl)
        assertEquals(Path.of("/tmp/$id.thumb.avif"), service.readyThumbnailFile(id))
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.DELETED).copy(thumbnailBytes = 10))
        assertThrows<ResponseStatusException> { service.readyThumbnailFile(id) }
    }

    @Test
    fun `thumbnail candidates retain appearance order while locking in stable image order`() {
        val second = "b".repeat(32)
        val secondUrl = "${properties.publicBaseUrl}/wiki/$second.avif"
        `when`(storage.publicUrl(id)).thenReturn(url)
        `when`(storage.publicUrl(second)).thenReturn(secondUrl)
        `when`(storage.thumbnailUrl(id)).thenReturn(url.removeSuffix(".avif") + ".thumb.avif")
        `when`(storage.thumbnailUrl(second)).thenReturn(secondUrl.removeSuffix(".avif") + ".thumb.avif")
        `when`(repository.lock(id)).thenReturn(record(WikiImageStatus.READY).copy(thumbnailBytes = 10))
        `when`(repository.lock(second)).thenReturn(record(WikiImageStatus.READY).copy(id = second, thumbnailBytes = 12))
        val candidates = service.thumbnailCandidates(listOf(secondUrl, "https://external.example/image.png", url, secondUrl), true)
        assertEquals(listOf(second, id), candidates.map { it.id })
        inOrder(repository).run {
            verify(repository).lock(id)
            verify(repository).lock(second)
        }
        `when`(repository.find(id)).thenReturn(record(WikiImageStatus.READY))
        `when`(repository.find(second)).thenReturn(record(WikiImageStatus.DELETED).copy(id = second, thumbnailBytes = 12))
        assertTrue(service.thumbnailCandidates(listOf(secondUrl, url)).isEmpty())
    }
}
