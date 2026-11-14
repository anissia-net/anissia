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
    fun `operator policy is checked before receiving bytes or creating jobs`() {
        `when`(access.requireImageUpload(actor)).thenThrow(FailException("이미지 업로드 권한 없음"))
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
        `when`(repository.lock(id)).thenReturn(record(WikiImageStatus.READY).copy(documentKind = "anime", documentNo = 11))
        service.requireReadyUrls(setOf(url), "anime", 11)
        for (invalid in listOf("https://evil.example/wiki/$id.avif", "$url?x=1", "${properties.publicBaseUrl}/wiki/../$id.avif")) {
            assertThrows<FailException> { service.requireReadyUrls(setOf(invalid), "anime", 11) }
        }
        `when`(repository.lock(id)).thenReturn(record(WikiImageStatus.PROCESSING))
        assertThrows<FailException> { service.requireReadyUrls(setOf(url), "anime", 11) }
    }

    @Test
    fun `metadata cannot introduce active urls or credentials`() {
        assertTrue(WikiImageService.validSource("https://example.com/image?a=1"))
        assertTrue(WikiImageService.validSource(""))
        assertFalse(WikiImageService.validSource("javascript:alert(1)"))
        assertFalse(WikiImageService.validSource("https://user:password@example.com/image"))
        `when`(access.requireImageUpload(actor)).thenReturn(Account(an = actor.an))
        assertThrows<IllegalArgumentException> {
            service.upload(MockMultipartFile("file", byteArrayOf(1)), "a".repeat(501), "", actor)
        }
        verifyNoInteractions(storage)
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

    @Test
    fun `document image references reject another owner and ownerless legacy images`() {
        `when`(storage.publicUrl(id)).thenReturn(url)
        for (image in listOf(record(WikiImageStatus.READY),
            record(WikiImageStatus.READY).copy(documentKind = "anime", documentNo = 12),
            record(WikiImageStatus.READY).copy(documentKind = "template", documentNo = 11))) {
            `when`(repository.lock(id)).thenReturn(image)
            assertThrows<FailException> { service.requireReadyUrls(setOf(url), "anime", 11) }
        }
        assertThrows<FailException> { service.requireReadyUrls(setOf(url)) }
        service.requireReadyUrls(emptySet())
    }

    @Test
    fun `archive list requires operator while document list is limited to its owner`() {
        `when`(access.requireOperator(actor)).thenThrow(FailException("운영자만 가능"))
        assertThrows<FailException> { service.list(0, "", actor = actor) }
        verifyNoInteractions(repository)
        `when`(repository.list(0, "사진", false, "anime", 11)).thenReturn(emptyList<WikiImage>() to 0L)
        assertTrue(service.list(0, "사진", actor = actor, documentKind = "anime", documentNo = 11).content.isEmpty())
        verify(repository).requireDocument("anime", 11)
        verify(repository).list(0, "사진", false, "anime", 11)
    }

    @Test
    fun `deleted upload target fails before consuming file bytes`() {
        `when`(access.requireImageUpload(actor)).thenReturn(Account(an = actor.an))
        org.mockito.Mockito.doThrow(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))
            .`when`(repository).requireDocument("anime", 11, true)
        assertThrows<ResponseStatusException> { service.upload(MockMultipartFile("file", byteArrayOf(1)), "", "", actor, "anime", 11) }
        verifyNoInteractions(storage)
    }

    @Test
    fun `request approval checks target before publishing the request`() {
        val request = record(WikiImageStatus.REVIEW_READY).copy(submissionKind = "REQUEST", documentKind = "template", documentNo = 11)
        `when`(repository.find(id)).thenReturn(request)
        `when`(access.requireOperator(actor)).thenReturn(Account(an = actor.an))
        org.mockito.Mockito.doThrow(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))
            .`when`(repository).requireDocument("template", 11, true)
        assertThrows<ResponseStatusException> { service.approveRequest(id, actor) }
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).approveRequest(id)
        verifyNoInteractions(storage)
    }

    @Test
    fun `reattaching archive image returns existing independent copy without copying again`() {
        val original = record(WikiImageStatus.READY)
        val attached = original.copy(id = "b".repeat(32), documentKind = "anime", documentNo = 11, sourceImageId = id)
        `when`(access.requireImageUpload(actor)).thenReturn(Account(an = actor.an))
        `when`(repository.find(id)).thenReturn(original)
        `when`(repository.lock(id)).thenReturn(original)
        `when`(repository.attachedCopy(id, "anime", 11)).thenReturn(attached)
        val result = service.attach(id, WikiImageAttachRequest("anime", 11), actor)
        assertEquals(attached.id, result.id)
        assertEquals("anime", result.documentKind)
        assertEquals(11L, result.documentNo)
        assertEquals(id, result.sourceImageId)
        verify(storage).publicUrl(attached.id)
        org.mockito.Mockito.verifyNoMoreInteractions(storage)
        verifyNoInteractions(logs)
        inOrder(repository).run {
            verify(repository).requireDocument("anime", 11, true)
            verify(repository).find(id)
            verify(repository).lock(id)
        }
    }

    @Test
    fun `upload request and replacement enforce separate file capabilities before accessing files`() {
        `when`(access.requireImageUploadRequest(actor)).thenThrow(FailException("파일 업로드 요청 권한 없음"))
        `when`(access.requireImageFileReplacement(actor)).thenThrow(FailException("파일 교체 권한 없음"))
        val file = MockMultipartFile("file", byteArrayOf(1))
        assertThrows<FailException> { service.receiveRequest(file, "설명", "https://example.com", actor, "anime", 11) }
        assertThrows<FailException> { service.replace(id, file, 1, "설명", "https://example.com", actor) }
        verify(access).requireImageUploadRequest(actor)
        verify(access).requireImageFileReplacement(actor)
        org.mockito.Mockito.verify(access, org.mockito.Mockito.never()).requireContributor(actor)
        verifyNoInteractions(repository, storage)
    }
}
