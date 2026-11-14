package anissia.wiki

import anissia.account.domain.Account
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
    val canUploadImage: Boolean = false,
    val canRequestImageUpload: Boolean = false,
    val canReplaceImageFile: Boolean = false,
)

@Service
class WikiAccessService(private val accounts: AccountRepository) {
    @Transactional(readOnly = true)
    fun access(actor: Actor): WikiAccess = decision(
        if (actor.isLogin) accounts.findWithRolesByAn(actor.an) else null,
        OffsetDateTime.now(),
    )

    @Transactional(readOnly = true)
    fun requireMember(actor: Actor): Account {
        actor.validateLogin()
        val account = accounts.findWithRolesByAn(actor.an)
            ?: throw FailException("존재하지 않는 회원입니다.")
        if (account.isBan) throw FailException("차단된 계정은 위키에 참여할 수 없습니다.")
        return account
    }

    @Transactional(readOnly = true)
    fun requireContributor(actor: Actor): Account {
        val account = requireMember(actor)
        val access = decision(account, OffsetDateTime.now())
        if (!access.canContribute) throw FailException(access.message)
        return account
    }

    @Transactional(readOnly = true)
    fun requireOperator(actor: Actor): Account {
        val account = requireMember(actor)
        val access = decision(account, OffsetDateTime.now())
        if (!access.isOperator) throw FailException(access.message.ifBlank { "운영자 권한이 필요합니다." })
        return account
    }

    @Transactional(readOnly = true)
    fun requireImageUpload(actor: Actor): Account = requireImagePermission(actor, WikiAccess::canUploadImage, "이미지 직접 업로드 권한이 필요합니다.")

    @Transactional(readOnly = true)
    fun requireImageUploadRequest(actor: Actor): Account = requireImagePermission(actor, WikiAccess::canRequestImageUpload, "이미지 업로드 요청 권한이 필요합니다.")

    @Transactional(readOnly = true)
    fun requireImageFileReplacement(actor: Actor): Account = requireImagePermission(actor, WikiAccess::canReplaceImageFile, "이미지 파일 교체 권한이 필요합니다.")

    private fun requireImagePermission(actor: Actor, permitted: (WikiAccess) -> Boolean, message: String): Account {
        val account = requireMember(actor)
        val access = decision(account, OffsetDateTime.now())
        if (!permitted(access)) throw FailException(access.message.ifBlank { message })
        return account
    }

    companion object {
        fun decision(account: Account?, now: OffsetDateTime): WikiAccess {
            if (account == null) return WikiAccess(false, false, null, "로그인이 필요합니다.")
            val eligibleAt = account.regDt.plusDays(15)
            if (account.banExpireDt?.isAfter(now) == true) {
                return WikiAccess(false, false, eligibleAt, "차단된 계정은 위키에 참여할 수 없습니다.")
            }
            val eligible = account.isAdmin || !now.isBefore(eligibleAt)
            return WikiAccess(eligible, account.isAdmin, eligibleAt,
                if (eligible) "" else "위키 참여는 가입 후 15일이 지나면 가능합니다.",
                canUploadImage = account.isAdmin, canRequestImageUpload = eligible, canReplaceImageFile = eligible)
        }
    }
}

@RestController
class WikiAccessController(private val access: WikiAccessService) {
    @GetMapping("/wiki/access")
    fun get(actor: Actor) = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(access.access(actor)))
}
