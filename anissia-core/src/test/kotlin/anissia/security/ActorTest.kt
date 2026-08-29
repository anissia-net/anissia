package anissia.security

import anissia.account.domain.AccountRole
import anissia.support.FailException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Actor 권한 및 유효성")
class ActorTest {

    @Test
    fun `an 이 0 이면 비로그인 상태이다`() {
        val actor = Actor()
        assertFalse(actor.isLogin)
        assertFalse(actor.isAdmin)
        assertFalse(actor.isRoot)
        assertFalse(actor.isTranslator)
    }

    @Test
    fun `an 이 양수이고 ROOT 롤이면 root, admin 모두 true 이다`() {
        val actor = Actor(an = 1L, roles = listOf(AccountRole.ROOT.name))
        assertTrue(actor.isLogin)
        assertTrue(actor.isRoot)
        assertTrue(actor.isAdmin)
    }

    @Test
    fun `TRANSLATOR 만 있으면 admin true, root false 이다`() {
        val actor = Actor(an = 2L, roles = listOf(AccountRole.TRANSLATOR.name))
        assertTrue(actor.isAdmin)
        assertTrue(actor.isTranslator)
        assertFalse(actor.isRoot)
    }

    @Test
    fun `validateLogin 은 비로그인 시 FailException 을 던진다`() {
        val ex = assertThrows(FailException::class.java) { Actor().validateLogin() }
        assertEquals("로그인이 필요합니다.", ex.message)
    }

    @Test
    fun `validateAdmin 은 관리자가 아닐 때 FailException 을 던진다`() {
        val ex = assertThrows(FailException::class.java) { Actor(an = 1L).validateAdmin() }
        assertEquals("권한이 없습니다.", ex.message)
    }

    @Test
    fun `validateRoot 는 root 가 아닐 때 FailException 을 던진다`() {
        assertThrows(FailException::class.java) {
            Actor(an = 1L, roles = listOf(AccountRole.TRANSLATOR.name)).validateRoot()
        }
    }

    @Test
    fun `로그인 상태에서 validateLogin 은 정상 통과한다`() {
        Actor(an = 10L).validateLogin()
    }

    @Test
    fun `관리자(ROOT) 는 validateAdmin, validateRoot 모두 통과한다`() {
        val actor = Actor(an = 10L, roles = listOf(AccountRole.ROOT.name))
        actor.validateAdmin()
        actor.validateRoot()
    }

    @Test
    fun `anonymous 는 ip 만 유지한 비로그인 액터이다`() {
        val actor = Actor.anonymous("1.2.3.4")
        assertFalse(actor.isLogin)
        assertEquals("1.2.3.4", actor.ip)
    }
}
