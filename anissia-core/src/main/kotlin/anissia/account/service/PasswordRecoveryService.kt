package anissia.account.service

import anissia.account.api.dto.CompleteRecoverPasswordRequest
import anissia.account.api.dto.RecoverPasswordRequest
import anissia.account.api.dto.TokenOnlyRequest
import anissia.account.domain.AccountRecoverAuth
import anissia.account.repository.AccountRecoverAuthRepository
import anissia.account.repository.AccountRepository
import anissia.external.mail.MailSender
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.DateFormats
import me.saro.kit.TextKit
import org.springframework.beans.factory.annotation.Value
import anissia.security.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class PasswordRecoveryService(
    private val accountRepository: AccountRepository,
    private val accountRecoverAuthRepository: AccountRecoverAuthRepository,
    private val passwordHasher: PasswordHasher,
    private val mailSender: MailSender,
    @Value("\${host}") private val host: String,
) {
    private val template = javaClass.getResource("/email/account-recover-auth.html")!!.readText()

    @Transactional
    fun request(request: RecoverPasswordRequest, actor: Actor): ApiResponse<Unit> {
        request.validate()

        val account = accountRepository.findByEmailAndName(request.email, request.name)
            ?: return ApiResponse.ok()

        if (accountRecoverAuthRepository.existsByAnAndExpDtAfter(account.an, OffsetDateTime.now())) {
            return ApiResponse.fail("인증을 시도한 계정은 ${EXP_HOUR}시간동안 인증을 할 수 없습니다.")
        }

        val ip = actor.ip
        val auth = accountRecoverAuthRepository.save(
            AccountRecoverAuth(
                token = TextKit.generateBase62(128, 256),
                an = account.an,
                ip = ip,
                expDt = OffsetDateTime.now().plusHours(EXP_HOUR),
            ),
        )

        mailSender.sendAsync(
            request.email,
            "[애니시아] 계정 복원 이메일 인증",
            template
                .replace("[[ip]]", ip)
                .replace("[[exp_dt]]", DateFormats.USER_YMDHMS.format(auth.expDt))
                .replace("[[url]]", "$host/recover/${auth.no}-${auth.token}"),
        )

        return ApiResponse.ok()
    }

    @Transactional
    fun validate(request: TokenOnlyRequest): ApiResponse<Unit> {
        request.validate()

        return findUsableAuth(request.tokenNo, request.token)
            ?.let { ApiResponse.ok() }
            ?: ApiResponse.fail("")
    }

    @Transactional
    fun complete(request: CompleteRecoverPasswordRequest): ApiResponse<Unit> {
        request.validate()

        val auth = findUsableAuth(request.tokenNo, request.token)
            ?: return ApiResponse.fail("이메일 인증이 만료되었습니다.")

        val account = auth.account
            ?: return ApiResponse.fail("해당 메일인증에서 계정정보를 찾을 수 없습니다.")

        accountRecoverAuthRepository.save(auth.apply { usedDt = OffsetDateTime.now() })
        accountRepository.save(account.apply { password = passwordHasher.hash(request.password) })

        return ApiResponse.ok()
    }

    private fun findUsableAuth(tokenNo: Long, token: String): AccountRecoverAuth? =
        accountRecoverAuthRepository.findByNoAndTokenAndExpDtAfterAndUsedDtNull(
            tokenNo,
            token,
            OffsetDateTime.now(),
        )

    companion object {
        private const val EXP_HOUR = 1L
    }
}
