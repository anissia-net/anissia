package anissia.session.api.dto

import anissia.support.Texts
import anissia.support.failIf
import anissia.support.tokenNumber
import anissia.support.tokenValue

class DatAuthInfoItem(
    val dat: String = "",
    val token: String = "",
)

data class UserLoginRequest(
    val email: String = "",
    val password: String = "",
    val makeLoginToken: Boolean = false,
) {
    fun validate() {
        failIf(email.length < 4 || email.length > 64 || !Texts.isMail(email)) {
            "아이디는 E-MAIL 형식입니다."
        }
        failIf(password.length < 8 || password.length > 128) {
            "암호는 8자리 이상 128자리 이하로 작성해야합니다."
        }
    }
}

data class TokenLoginRequest(
    val absoluteToken: String = "",
) {
    val tokenNo: Long get() = absoluteToken.tokenNumber
    val token: String get() = absoluteToken.tokenValue
}
