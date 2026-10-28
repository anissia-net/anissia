package anissia.wiki

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.account.repository.AccountRepository
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.FailException
import org.springframework.stereotype.Service
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

data class WikiAccess(
    val canContribute: Boolean,
    val isOperator: Boolean,
    val eligibleAt: OffsetDateTime?,
    val message: String,
)

@Service
class WikiAccessService(private val accounts: AccountRepository) {
    @Transactional(readOnly = true)
    fun access(actor: Actor): WikiAccess = decision(
        if (actor.isLogin) accounts.findWithRolesByAn(actor.an) else null,
        OffsetDateTime.now(),
    )

    @Transactional(readOnly = true)
    fun requireContributor(actor: Actor): Account {
        actor.validateLogin()
        val account = accounts.findWithRolesByAn(actor.an)
            ?: throw FailException("존재하지 않는 회원입니다.")
        val access = decision(account, OffsetDateTime.now())
        if (!access.canContribute) throw FailException(access.message)
        return account
    }

    @Transactional(readOnly = true)
    fun requireOperator(actor: Actor): Account {
        actor.validateLogin()
        val account = accounts.findWithRolesByAn(actor.an)
            ?: throw FailException("존재하지 않는 회원입니다.")
        val access = decision(account, OffsetDateTime.now())
        if (!access.isOperator) throw FailException(access.message.ifBlank { "운영자 권한이 필요합니다." })
        return account
    }

    companion object {
        fun decision(account: Account?, now: OffsetDateTime): WikiAccess {
            if (account == null) return WikiAccess(false, false, null, "로그인이 필요합니다.")
            val eligibleAt = account.regDt.plusDays(15)
            if (account.banExpireDt?.isAfter(now) == true) {
                return WikiAccess(false, false, eligibleAt, "차단된 계정은 위키에 참여할 수 없습니다.")
            }
            // 임시 제한: 위키 공개 작성 재개 시 이 ROOT 전용 조건을 제거한다.
            if (AccountRole.ROOT !in account.roles) {
                return WikiAccess(false, false, eligibleAt, "현재 위키 작성과 이미지 업로드는 ROOT 권한만 가능합니다.")
            }
            val eligible = account.isAdmin || !now.isBefore(eligibleAt)
            return WikiAccess(eligible, account.isAdmin, eligibleAt,
                if (eligible) "" else "위키 작성과 이미지 업로드는 가입 후 15일이 지나면 가능합니다.")
        }
    }
}

@RestController
class WikiAccessController(private val access: WikiAccessService) {
    @GetMapping("/wiki/access")
    fun get(actor: Actor) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(access.access(actor)))
}
