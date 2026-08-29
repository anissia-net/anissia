package anissia.account.service

import anissia.account.api.dto.RegisterRequest
import anissia.account.api.dto.TokenOnlyRequest
import anissia.account.domain.Account
import anissia.account.domain.AccountRegisterAuth
import anissia.account.repository.AccountBanNameRepository
import anissia.account.repository.AccountRegisterAuthRepository
import anissia.account.repository.AccountRepository
import anissia.external.mail.MailSender
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.DateFormats
import anissia.support.Json
import me.saro.kit.TextKit
import org.springframework.beans.factory.annotation.Value
import anissia.security.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RegistrationService(
    private val accountRepository: AccountRepository,
    private val accountBanNameRepository: AccountBanNameRepository,
    private val accountRegisterAuthRepository: AccountRegisterAuthRepository,
    private val passwordHasher: PasswordHasher,
    private val mailSender: MailSender,
    @Value("\${host}") private val host: String,
) {
    private val template = javaClass.getResource("/email/account-register-auth.html")!!.readText()

    @Transactional
    fun request(request: RegisterRequest, actor: Actor): ApiResponse<Unit> {
        request.validate()

        if (actor.isLogin) {
            return ApiResponse.fail("로그인 중에는 계정을 생성할 수 없습니다.")
        }

        if (accountRepository.existsByEmail(request.email)) {
            return ApiResponse.fail("이미 가입된 계정입니다.")
        }

        if (accountRepository.existsByName(request.name) || accountBanNameRepository.existsById(request.name)) {
            return ApiResponse.fail("사용중이거나 사용할 수 없는 이름입니다.")
        }

        if (accountRegisterAuthRepository.existsByEmailAndExpDtAfter(request.email, OffsetDateTime.now())) {
            return ApiResponse.fail("인증을 시도한 계정은 ${EXP_HOUR}시간동안 인증을 할 수 없습니다.")
        }

        val ip = actor.ip
        val auth = accountRegisterAuthRepository.save(
            AccountRegisterAuth(
                token = TextKit.generateBase62(128, 256),
                email = request.email,
                ip = ip,
                data = Json.write(request.apply { password = passwordHasher.hash(password) }),
                expDt = OffsetDateTime.now().plusHours(1),
            ),
        )

        mailSender.sendAsync(
            request.email,
            "[애니시아] 회원가입 이메일 인증",
            template
                .replace("[[ip]]", ip)
                .replace("[[exp_dt]]", auth.expDt.format(DateFormats.USER_YMDHMS))
                .replace("[[url]]", "$host/register/${auth.no}-${auth.token}"),
        )

        return ApiResponse.ok()
    }

    @Transactional
    fun complete(request: TokenOnlyRequest): ApiResponse<Unit> {
        request.validate()

        val auth = accountRegisterAuthRepository.findByNoAndTokenAndExpDtAfterAndUsedDtNull(
            request.tokenNo,
            request.token,
            OffsetDateTime.now(),
        ) ?: return ApiResponse.fail("이메일 인증이 만료되었습니다.")

        val registered = Json.read<RegisterRequest>(auth.data)

        if (accountRepository.existsByEmail(registered.email)) {
            return ApiResponse.fail("이미 가입된 계정입니다.")
        }

        if (accountRepository.existsByName(registered.name)) {
            return ApiResponse.fail("사용중인 닉네임 입니다.")
        }

        accountRegisterAuthRepository.save(auth.apply { usedDt = OffsetDateTime.now() })

        accountRepository.save(
            Account(
                email = registered.email,
                password = registered.password,
                name = registered.name,
            ),
        )

        return ApiResponse.ok()
    }

    companion object {
        private const val EXP_HOUR = 1
    }
}
