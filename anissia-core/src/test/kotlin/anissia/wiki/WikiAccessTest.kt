package anissia.wiki

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.account.repository.AccountRepository
import anissia.security.Actor
import anissia.support.FailException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import java.time.OffsetDateTime

class WikiAccessTest {
    private val now = OffsetDateTime.parse("2026-10-27T10:00:00+09:00")

    @Test
    fun `일반 회원은 가입 후 정확히 15일부터 참여할 수 있다`() {
        val account = Account(regDt = now.minusDays(15))
        assertFalse(WikiAccessService.decision(account, now.minusNanos(1)).canContribute)
        for (at in listOf(now, now.plusYears(1))) {
            val access = WikiAccessService.decision(account, at)
            assertTrue(access.canContribute)
            assertFalse(access.isOperator)
            assertEquals("", access.message)
        }
    }

    @Test
    fun `자막 제작자 운영진도 가입 대기 없이 참여하고 처리할 수 있다`() {
        val account = Account(regDt = now, roles = mutableSetOf(AccountRole.TRANSLATOR))
        val access = WikiAccessService.decision(account, now)
        assertTrue(access.canContribute)
        assertTrue(access.isOperator)
    }

    @Test
    fun `ROOT는 대기기간을 면제하지만 차단은 면제하지 않는다`() {
        val account = Account(regDt = now, roles = mutableSetOf(AccountRole.ROOT))
        assertTrue(WikiAccessService.decision(account, now).canContribute)
        assertTrue(WikiAccessService.decision(account, now).isOperator)
        account.banExpireDt = now.plusDays(1)
        assertFalse(WikiAccessService.decision(account, now).canContribute)
        assertFalse(WikiAccessService.decision(account, now).isOperator)
    }

    @Test
    fun `세션의 오래된 운영자 역할로 현재 계정 검사를 우회하지 못한다`() {
        val accounts = mock(AccountRepository::class.java)
        `when`(accounts.findWithRolesByAn(7)).thenReturn(Account(an = 7, regDt = OffsetDateTime.now()))
        val service = WikiAccessService(accounts)
        val actor = Actor(an = 7, roles = listOf("ROOT"))
        assertThrows<FailException> { service.requireContributor(actor) }
        assertThrows<FailException> { service.requireOperator(actor) }
        assertFalse(service.access(actor).isOperator)
    }

    @Test
    fun `현재 계정의 ROOT 권한으로 작성과 관리 권한을 허용한다`() {
        val accounts = mock(AccountRepository::class.java)
        val account = Account(an = 7, roles = mutableSetOf(AccountRole.ROOT))
        `when`(accounts.findWithRolesByAn(7)).thenReturn(account)
        val service = WikiAccessService(accounts)
        val actor = Actor(an = 7)
        assertSame(account, service.requireContributor(actor))
        assertSame(account, service.requireOperator(actor))
        assertTrue(service.access(actor).canContribute)
        assertTrue(service.access(actor).isOperator)
        account.banExpireDt = OffsetDateTime.now().plusDays(1)
        assertThrows<FailException> { service.requireContributor(actor) }
        assertThrows<FailException> { service.requireOperator(actor) }
    }

    @Test
    fun `비회원과 삭제된 계정은 참여할 수 없다`() {
        assertFalse(WikiAccessService.decision(null, now).canContribute)
        val accounts = mock(AccountRepository::class.java)
        val service = WikiAccessService(accounts)
        assertThrows<FailException> { service.requireContributor(Actor()) }
        verifyNoInteractions(accounts)
        assertThrows<FailException> { service.requireContributor(Actor(an = 7)) }
    }

    @Test
    fun `가입 대기 중 회원도 자신의 기존 요청을 읽을 수 있지만 차단 계정은 읽을 수 없다`() {
        val accounts = mock(AccountRepository::class.java)
        val account = Account(an = 7, regDt = OffsetDateTime.now())
        `when`(accounts.findWithRolesByAn(7)).thenReturn(account)
        val service = WikiAccessService(accounts)
        assertSame(account, service.requireMember(Actor(an = 7)))
        assertThrows<FailException> { service.requireContributor(Actor(an = 7)) }
        account.banExpireDt = OffsetDateTime.now().plusDays(1)
        assertThrows<FailException> { service.requireMember(Actor(an = 7)) }
    }

    @Test
    fun `문서 편집자는 직접 파일 업로드 권한 없이 요청과 기존 파일 교체만 할 수 있다`() {
        val accounts = mock(AccountRepository::class.java)
        val account = Account(an = 7, regDt = OffsetDateTime.now().minusDays(15))
        `when`(accounts.findWithRolesByAn(7)).thenReturn(account)
        val service = WikiAccessService(accounts)
        val actor = Actor(an = 7, roles = listOf("ROOT"))
        val access = service.access(actor)
        assertTrue(access.canContribute)
        assertFalse(access.canUploadImage)
        assertTrue(access.canRequestImageUpload)
        assertTrue(access.canReplaceImageFile)
        assertSame(account, service.requireContributor(actor))
        assertThrows<FailException> { service.requireImageUpload(actor) }
        assertSame(account, service.requireImageUploadRequest(actor))
        assertSame(account, service.requireImageFileReplacement(actor))
    }

    @Test
    fun `파일 권한도 가입 15일 경계를 따르고 운영진은 대기기간을 면제한다`() {
        val member = Account(regDt = now.minusDays(15))
        val before = WikiAccessService.decision(member, now.minusNanos(1))
        assertFalse(before.canUploadImage)
        assertFalse(before.canRequestImageUpload)
        assertFalse(before.canReplaceImageFile)
        val eligible = WikiAccessService.decision(member, now)
        assertFalse(eligible.canUploadImage)
        assertTrue(eligible.canRequestImageUpload)
        assertTrue(eligible.canReplaceImageFile)
        for (role in listOf(AccountRole.ROOT, AccountRole.TRANSLATOR)) {
            val operator = Account(regDt = now, roles = mutableSetOf(role))
            val access = WikiAccessService.decision(operator, now)
            assertTrue(access.canUploadImage)
            assertTrue(access.canRequestImageUpload)
            assertTrue(access.canReplaceImageFile)
            operator.banExpireDt = now.plusDays(1)
            val banned = WikiAccessService.decision(operator, now)
            assertFalse(banned.canUploadImage)
            assertFalse(banned.canRequestImageUpload)
            assertFalse(banned.canReplaceImageFile)
        }
    }

    @Test
    fun `모든 파일 권한 검사는 비회원 삭제 계정과 차단 계정을 거부한다`() {
        val accounts = mock(AccountRepository::class.java)
        val service = WikiAccessService(accounts)
        val actions = listOf(service::requireImageUpload, service::requireImageUploadRequest, service::requireImageFileReplacement)
        actions.forEach { action -> assertThrows<FailException> { action(Actor()) } }
        verifyNoInteractions(accounts)
        actions.forEach { action -> assertThrows<FailException> { action(Actor(an = 7)) } }
        val account = Account(an = 7, regDt = OffsetDateTime.now(), roles = mutableSetOf(AccountRole.ROOT))
        `when`(accounts.findWithRolesByAn(7)).thenReturn(account)
        actions.forEach { action -> assertSame(account, action(Actor(an = 7))) }
        account.banExpireDt = OffsetDateTime.now().plusDays(1)
        actions.forEach { action -> assertThrows<FailException> { action(Actor(an = 7)) } }
        val anonymous = service.access(Actor())
        assertFalse(anonymous.canUploadImage)
        assertFalse(anonymous.canRequestImageUpload)
        assertFalse(anonymous.canReplaceImageFile)
    }
}
