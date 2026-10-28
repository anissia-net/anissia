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
    fun `15일이 지난 일반 회원도 임시 제한 중에는 참여할 수 없다`() {
        val account = Account(regDt = now.minusDays(15))
        for (at in listOf(now.minusNanos(1), now, now.plusYears(1))) {
            val access = WikiAccessService.decision(account, at)
            assertFalse(access.canContribute)
            assertFalse(access.isOperator)
            assertTrue(access.message.contains("ROOT"))
        }
    }

    @Test
    fun `ROOT가 아닌 운영자도 임시 제한 중에는 참여할 수 없다`() {
        val account = Account(regDt = now.minusYears(1), roles = mutableSetOf(AccountRole.TRANSLATOR))
        val access = WikiAccessService.decision(account, now)
        assertFalse(access.canContribute)
        assertFalse(access.isOperator)
        assertTrue(access.message.contains("ROOT"))
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
        `when`(accounts.findWithRolesByAn(7)).thenReturn(Account(an = 7, regDt = now.minusYears(1), roles = mutableSetOf(AccountRole.TRANSLATOR)))
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
}
