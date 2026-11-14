package anissia.wiki

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.security.Actor
import anissia.support.FailException
import anissia.support.Json
import anissia.wiki.upload.WikiImageDto
import anissia.wiki.upload.WikiImageOperationRequest
import anissia.wiki.upload.WikiImageService
import anissia.wiki.upload.WikiImageStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional

class WikiRequestServiceTest {
    private val access = mock(WikiAccessService::class.java)
    private val requests = mock(WikiRequestRepository::class.java)
    private val templates = mock(WikiTemplateRepository::class.java)
    private val content = mock(WikiContentService::class.java)
    private val images = mock(WikiImageService::class.java)
    private val service = WikiRequestService(access, requests, templates, content, images)
    private val member = Account(an = 7, name = "요청자")
    private val operator = Account(an = 9, name = "처리자", roles = mutableSetOf(AccountRole.TRANSLATOR))
    private val memberActor = Actor(an = 7)
    private val operatorActor = Actor(an = 9)
    private val body = WikiBody(blocks = Json.readTree("""[{"w":"p","ch":["요청한 내용"]}]"""))

    @BeforeEach
    fun allowAccounts() {
        `when`(access.requireMember(memberActor)).thenReturn(member)
        `when`(access.requireMember(operatorActor)).thenReturn(operator)
        `when`(access.requireContributor(memberActor)).thenReturn(member)
        `when`(access.requireOperator(operatorActor)).thenReturn(operator)
        `when`(requests.saveAndFlush(any(WikiRequest::class.java))).thenAnswer {
            it.getArgument<WikiRequest>(0).apply { id = 41 }
        }
    }

    private fun templateRequest() = WikiRequest(id = 41, requesterAn = member.an, createdBy = member.name,
        kind = WikiRequestKind.TEMPLATE_CREATE, title = "시리즈", body = Json.write(body), reason = "공통 정보가 필요합니다.")

    @Test
    fun `other members cannot discover a private request or its image`() {
        val other = WikiRequest(id = 41, requesterAn = 8, kind = WikiRequestKind.IMAGE_CREATE, imageId = "a".repeat(32))
        `when`(requests.findById(41)).thenReturn(Optional.of(other))
        assertEquals(HttpStatus.NOT_FOUND, assertThrows<ResponseStatusException> { service.get(41, memberActor) }.statusCode)
        assertEquals(HttpStatus.NOT_FOUND, assertThrows<ResponseStatusException> { service.imageFile(41, memberActor) }.statusCode)
        verifyNoInteractions(images)
    }

    @Test
    fun `list uses current database roles and never the actor role claims`() {
        val forgedActor = Actor(an = 7, roles = listOf("ROOT"))
        `when`(access.requireMember(forgedActor)).thenReturn(member)
        `when`(requests.visibleRequests(7, false, WikiRequestStatus.PENDING, PageRequest.of(0, 30)))
            .thenReturn(PageImpl(listOf(templateRequest()), PageRequest.of(0, 30), 1))
        val result = service.list(0, WikiRequestStatus.PENDING, forgedActor)
        assertEquals(1L, result.totalElements)
        assertNull(result.content.single().body)
        assertFalse(result.content.single().canProcess)
        verify(requests).visibleRequests(7, false, WikiRequestStatus.PENDING, PageRequest.of(0, 30))
    }

    @Test
    fun `template submission preserves the draft without registering a public template`() {
        val result = service.submit(WikiRequestSubmission(WikiRequestKind.TEMPLATE_CREATE,
            title = " 시리즈 ", body = body, reason = " 공통 정보가 필요합니다. "), memberActor)
        assertEquals(WikiRequestStatus.PENDING, result.status)
        assertEquals("시리즈", result.title)
        assertEquals(body, result.body)
        assertTrue(result.canCancel)
        assertFalse(result.canApprove)
        verifyNoInteractions(content)
        verifyNoInteractions(images)
        val saved = ArgumentCaptor.forClass(WikiRequest::class.java)
        verify(requests).saveAndFlush(saved.capture())
        assertEquals(7L, saved.value.requesterAn)
        assertEquals("요청자", saved.value.createdBy)
    }

    @Test
    fun `template creation requests reject images until the template has been approved`() {
        val imageBody = WikiBody(blocks = Json.readTree("""[{"w":"img","a":{"src":"https://files.example/wiki/${"a".repeat(32)}.avif"}}]"""))
        val failure = assertThrows<FailException> {
            service.submit(WikiRequestSubmission(WikiRequestKind.TEMPLATE_CREATE, title = "이미지 템플릿", body = imageBody, reason = "등록 요청"), memberActor)
        }
        assertTrue(failure.message!!.contains("승인받은 뒤"))
        verifyNoInteractions(requests, content, images)
    }

    @Test
    fun `only operators can approve and create a template from the preserved draft`() {
        val request = templateRequest()
        `when`(requests.lockRequest(41)).thenReturn(request)
        `when`(access.requireOperator(memberActor)).thenThrow(FailException("운영자 권한이 필요합니다."))
        assertThrows<FailException> { service.approve(41, WikiRequestDecision("확인 완료"), memberActor) }
        verifyNoInteractions(content)
        `when`(content.createTemplate(WikiEditRequest(body = body, title = "시리즈", summary = "문서 요청 #41 승인"), operatorActor))
            .thenReturn(WikiDocument(templateNo = 22, title = "시리즈", body = body, revision = 1))
        val result = service.approve(41, WikiRequestDecision(" 확인 완료 "), operatorActor)
        assertEquals(WikiRequestStatus.APPROVED, result.status)
        assertEquals(22L, result.resultTargetNo)
        assertEquals("처리자", result.processedBy)
        assertEquals(9L, request.processedAn)
        assertEquals("확인 완료", result.processingReason)
        assertFalse(result.canProcess)
        assertFalse(result.canCancel)
        assertEquals(HttpStatus.CONFLICT,
            assertThrows<ResponseStatusException> { service.approve(41, WikiRequestDecision("재시도"), operatorActor) }.statusCode)
        verify(content, times(1)).createTemplate(WikiEditRequest(body = body, title = "시리즈", summary = "문서 요청 #41 승인"), operatorActor)
    }

    @Test
    fun `failed approval keeps the request pending and does not record a processor`() {
        val request = WikiRequest(id = 41, kind = WikiRequestKind.TEMPLATE_DELETE, requesterAn = 7,
            targetNo = 22, baseRevision = 2)
        `when`(requests.lockRequest(41)).thenReturn(request)
        doThrow(FailException("사용 중인 템플릿입니다.")).`when`(content).deleteTemplate(22, 2, operatorActor)
        assertThrows<FailException> { service.approve(41, WikiRequestDecision("삭제 승인"), operatorActor) }
        assertEquals(WikiRequestStatus.PENDING, request.status)
        assertNull(request.processedBy)
        assertNull(request.processedAt)
    }

    @Test
    fun `deletion request captures the reviewed revision and rejects stale submissions`() {
        val template = WikiTemplate(templateNo = 22, title = "시리즈").apply { revision = 3 }
        `when`(templates.lockTemplate(22)).thenReturn(template)
        val submission = WikiRequestSubmission(WikiRequestKind.TEMPLATE_DELETE, targetNo = 22, baseRevision = 2, reason = "중복 내용")
        assertEquals(HttpStatus.CONFLICT, assertThrows<ResponseStatusException> { service.submit(submission, memberActor) }.statusCode)
        verifyNoInteractions(requests, content)
        val result = service.submit(submission.copy(baseRevision = 3), memberActor)
        assertEquals(22L, result.targetNo)
        val saved = ArgumentCaptor.forClass(WikiRequest::class.java)
        verify(requests).saveAndFlush(saved.capture())
        assertEquals(3L, saved.value.baseRevision)
    }

    @Test
    fun `only the requester can cancel and cancellation blocks later approval`() {
        val request = templateRequest()
        `when`(requests.lockRequest(41)).thenReturn(request)
        assertEquals(HttpStatus.NOT_FOUND, assertThrows<ResponseStatusException> { service.cancel(41, operatorActor) }.statusCode)
        assertEquals(WikiRequestStatus.PENDING, request.status)
        val result = service.cancel(41, memberActor)
        assertEquals(WikiRequestStatus.CANCELLED, result.status)
        assertEquals("요청자", result.processedBy)
        assertThrows<ResponseStatusException> { service.approve(41, WikiRequestDecision("승인"), operatorActor) }
        verifyNoInteractions(content, images)
    }

    @Test
    fun `image approval and rejection cannot bypass the private image lifecycle`() {
        val imageId = "a".repeat(32)
        val request = WikiRequest(id = 41, requesterAn = 7, kind = WikiRequestKind.IMAGE_CREATE, imageId = imageId)
        `when`(requests.lockRequest(41)).thenReturn(request)
        `when`(images.approveRequest(imageId, operatorActor)).thenThrow(FailException("변환 중입니다."))
        assertThrows<FailException> { service.approve(41, WikiRequestDecision("승인"), operatorActor) }
        assertEquals(WikiRequestStatus.PENDING, request.status)
        val result = service.reject(41, WikiRequestDecision("출처 확인이 필요합니다."), operatorActor)
        assertEquals(WikiRequestStatus.REJECTED, result.status)
        verify(images).discardRequest(imageId, operatorActor)
        assertThrows<ResponseStatusException> { service.cancel(41, memberActor) }
        verifyNoInteractions(content)
    }

    @Test
    fun `image deletion approval uses the submitted revision rather than the latest one`() {
        val imageId = "b".repeat(32)
        val request = WikiRequest(id = 41, requesterAn = 7, kind = WikiRequestKind.IMAGE_DELETE, imageId = imageId, baseRevision = 4)
        `when`(requests.lockRequest(41)).thenReturn(request)
        val deleted = WikiImageDto(imageId, WikiImageStatus.DELETED, "이미지", "https://example.com", Instant.now())
        `when`(images.delete(imageId, WikiImageOperationRequest(4), operatorActor)).thenReturn(deleted)
        val result = service.approve(41, WikiRequestDecision("삭제 승인"), operatorActor)
        assertEquals(imageId, result.resultImageId)
        verify(images).delete(imageId, WikiImageOperationRequest(4), operatorActor)
    }

    @Test
    fun `missing reason and empty drafts cannot create or approve a request`() {
        assertThrows<FailException> { service.submit(WikiRequestSubmission(WikiRequestKind.TEMPLATE_CREATE, title = "제목", body = body), memberActor) }
        assertThrows<FailException> { service.submit(WikiRequestSubmission(WikiRequestKind.TEMPLATE_CREATE, title = "제목", body = WikiBody.empty(), reason = "등록"), memberActor) }
        verifyNoInteractions(requests, content)
        `when`(requests.lockRequest(41)).thenReturn(templateRequest())
        assertThrows<FailException> { service.approve(41, WikiRequestDecision(" "), operatorActor) }
        verifyNoInteractions(content)
    }
}
