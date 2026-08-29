package anissia.session.service

import anissia.account.repository.AccountRepository
import anissia.security.Actor
import anissia.security.DatTokenService
import anissia.session.api.dto.DatAuthInfoItem
import anissia.session.api.dto.TokenLoginRequest
import anissia.session.api.dto.UserLoginRequest
import anissia.session.domain.LoginFail
import anissia.session.domain.LoginPass
import anissia.session.domain.LoginToken
import anissia.session.repository.LoginFailRepository
import anissia.session.repository.LoginPassRepository
import anissia.session.repository.LoginTokenRepository
import anissia.support.ApiResponse
import anissia.support.fail
import anissia.support.logger
import anissia.security.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class LoginService(
    private val passwordHasher: PasswordHasher,
    private val accountRepository: AccountRepository,
    private val loginFailRepository: LoginFailRepository,
    private val loginPassRepository: LoginPassRepository,
    private val loginTokenRepository: LoginTokenRepository,
    private val datTokenService: DatTokenService,
) {
    private val log = logger<LoginService>()

    @Transactional
    fun loginByPassword(request: UserLoginRequest, actor: Actor): ApiResponse<DatAuthInfoItem> {
        request.validate()
        val ip = actor.ip

        if (isBlockedByTooManyFailures(ip, request.email)) {
            log.info("잦은 로그인 시도 : {}", request.email)
            fail("잦은 접속시도로 일정시간동안 차단되었습니다.\n시간이 지난 후 다시 시도해주세요.")
        }

        val account = accountRepository.findWithRolesByEmail(request.email)
            ?.takeIf { passwordHasher.matches(request.password, it.password) }

        if (account == null) {
            loginFailRepository.save(LoginFail.create(ip = ip, email = request.email))
            fail("암호가 일치하지 않거나 존재하지 않는 계정입니다.")
        }

        accountRepository.save(account.apply { lastLoginDt = OffsetDateTime.now() })

        return issue(Actor.of(account, ip), request.makeLoginToken)
    }

    @Transactional
    fun loginByToken(request: TokenLoginRequest, actor: Actor): ApiResponse<DatAuthInfoItem> {
        val ip = actor.ip

        if (isBlockedByTooManyFailures(ip, "#${request.tokenNo}")) {
            fail("잦은 접속시도로 일정시간동안 차단되었습니다.\n시간이 지난 후 다시 시도해주세요.")
        }

        loginTokenRepository
            .findByTokenNoAndTokenAndExpDtAfter(request.tokenNo, request.token, OffsetDateTime.now())
            ?.let { token -> accountRepository.findWithRolesByAn(token.an) }
            ?.also { account ->
                accountRepository.save(account.apply { lastLoginDt = OffsetDateTime.now() })
                return issue(Actor.of(account, ip), true)
            }

        loginFailRepository.save(LoginFail.create(ip = ip, email = "#${request.tokenNo}"))

        fail("기타예외")
    }

    @Transactional
    fun refresh(actor: Actor): ApiResponse<DatAuthInfoItem> {
        if (actor.isLogin) {
            accountRepository.findWithRolesByAn(actor.an)
                ?.let { return issue(Actor.of(it, actor.ip), false) }
        }
        return ApiResponse.fail("유효하지 않은 토큰 정보입니다.", null)
    }

    private fun isBlockedByTooManyFailures(ip: String, email: String): Boolean =
        loginFailRepository.countByIpAndEmailAndFailDtAfter(ip, email, OffsetDateTime.now().plusMinutes(-30)) >= 10

    private fun issue(actor: Actor, makeLoginToken: Boolean): ApiResponse<DatAuthInfoItem> {
        val token = if (makeLoginToken) {
            loginTokenRepository.save(LoginToken.create(an = actor.an)).absoluteToken
        } else {
            ""
        }

        loginFailRepository.deleteByIpAndEmail(actor.ip, actor.email)
        loginPassRepository.save(LoginPass.create(an = actor.an, connType = "login", ip = actor.ip))

        return ApiResponse.ok(DatAuthInfoItem(datTokenService.issue(actor), token))
    }
}
