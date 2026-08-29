package anissia.security

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.support.DateFormats
import anissia.support.FailException
import com.fasterxml.jackson.annotation.JsonIgnore

class Actor(
    val an: Long = 0,
    val name: String = "",
    val email: String = "",
    val roles: List<String> = listOf(),
    val ip: String = "",
) {
    @get:JsonIgnore
    val isLogin: Boolean get() = an > 0L

    @get:JsonIgnore
    val isAdmin: Boolean
        get() = isLogin && (roles.contains(AccountRole.TRANSLATOR.name) || roles.contains(AccountRole.ROOT.name))

    @get:JsonIgnore
    val isTranslator: Boolean get() = isLogin && roles.contains(AccountRole.TRANSLATOR.name)

    @get:JsonIgnore
    val isRoot: Boolean get() = isLogin && roles.contains(AccountRole.ROOT.name)

    fun validateLogin() {
        if (!isLogin) throw FailException("로그인이 필요합니다.")
    }

    fun validateAdmin() {
        if (!isAdmin) throw FailException("권한이 없습니다.")
    }

    fun validateRoot() {
        if (!isRoot) throw FailException("권한이 없습니다.")
    }

    companion object {
        fun anonymous(ip: String): Actor = Actor(ip = ip)

        fun of(account: Account, ip: String): Actor {
            if (account.isBan) {
                throw FailException("차단된 계정입니다.\n해제일 ${account.banExpireDt!!.format(DateFormats.USER_YMDHMS)}")
            }
            return Actor(
                an = account.an,
                name = account.name,
                email = account.email,
                roles = account.roles.map { it.name },
                ip = ip,
            )
        }
    }
}
