package anissia.account.api.dto

import anissia.account.domain.Account
import anissia.account.domain.AccountRole
import anissia.support.Texts
import anissia.support.badRequestIf
import anissia.support.badRequestUnless
import anissia.support.tokenNumber
import anissia.support.tokenValue

private const val NAME_MESSAGE = "닉네임은 특수문자를 제외한\n한글/영어/숫자/한자/일어로\n2자이상 16자 이하로 입력해주세요."

class AccountUserItem(
    val email: String,
    val name: String,
    val regTime: Long,
    val roles: Set<AccountRole>,
) {
    companion object {
        fun of(account: Account) = AccountUserItem(
            email = account.email,
            name = account.name,
            regTime = account.regDt.toEpochSecond(),
            roles = account.roles,
        )
    }
}

data class RegisterRequest(
    var email: String = "",
    var password: String = "",
    var name: String = "",
) {
    fun validate() {
        badRequestIf(email.isBlank()) { "계정(이메일)을 입력해주세요." }
        badRequestIf(password.isBlank()) { "암호를 입력해주세요." }
        badRequestIf(name.isBlank()) { "이름을 입력해주세요." }
        badRequestUnless(Texts.isMail(email)) { "이메일 형식이 아닙니다." }
        badRequestUnless(Texts.isName(name)) { NAME_MESSAGE }
    }
}

data class TokenOnlyRequest(
    val absoluteToken: String = "",
) {
    val tokenNo: Long get() = absoluteToken.tokenNumber
    val token: String get() = absoluteToken.tokenValue

    fun validate() {
        badRequestIf(absoluteToken.isBlank()) { "토큰이 없습니다." }
        badRequestIf(absoluteToken.length !in 128..600) { "토큰이 잘못되었습니다." }
    }
}

data class RecoverPasswordRequest(
    val email: String = "",
    val name: String = "",
) {
    fun validate() {
        badRequestIf(name.isBlank()) { "이름을 입력해주세요." }
        badRequestIf(email.isBlank()) { "계정(이메일)을 입력해주세요." }
        badRequestUnless(Texts.isMail(email)) { "이메일 형식이 아닙니다." }
        badRequestUnless(Texts.isName(name)) { NAME_MESSAGE }
    }
}

data class CompleteRecoverPasswordRequest(
    val absoluteToken: String = "",
    val password: String = "",
) {
    val tokenNo: Long get() = absoluteToken.tokenNumber
    val token: String get() = absoluteToken.tokenValue

    fun validate() {
        badRequestIf(password.isBlank()) { "암호를 입력해주세요." }
        badRequestIf(password.length !in 8..128) { "암호는 8자 이상 128자 이하로 입력해 주세요." }
        badRequestIf(absoluteToken.isBlank()) { "토큰이 없습니다." }
        badRequestIf(absoluteToken.length !in 128..600) { "토큰이 잘못되었습니다." }
    }
}

data class EditUserPasswordRequest(
    val oldPassword: String = "",
    val newPassword: String = "",
) {
    fun validate() {
        badRequestIf(oldPassword.isBlank()) { "암호를 입력해 주세요." }
        badRequestIf(newPassword.isBlank()) { "암호를 입력해 주세요." }
        badRequestIf(newPassword.length !in 8..128) { "암호는 8자 이상 128자 이하로 입력해 주세요." }
        badRequestIf(oldPassword == newPassword) { "새 암호가 기존 암호와 같습니다." }
    }
}

data class EditUserNameRequest(
    val password: String = "",
    val name: String = "",
) {
    fun validate() {
        badRequestIf(password.isBlank()) { "암호를 입력해 주세요." }
        badRequestIf(name.isBlank()) { "이름을 입력해주세요." }
        badRequestUnless(Texts.isName(name)) { NAME_MESSAGE }
    }
}

data class WithdrawRequest(
    val password: String = "",
) {
    fun validate() {
        badRequestIf(password.isBlank()) { "암호를 입력해 주세요." }
    }
}
