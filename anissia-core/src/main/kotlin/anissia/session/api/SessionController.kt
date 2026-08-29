package anissia.session.api

import anissia.security.Actor
import anissia.session.api.dto.DatAuthInfoItem
import anissia.session.api.dto.TokenLoginRequest
import anissia.session.api.dto.UserLoginRequest
import anissia.session.service.LoginService
import anissia.support.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/session")
class SessionController(
    private val loginService: LoginService,
) {
    @PostMapping
    fun doLogin(@RequestBody request: UserLoginRequest, actor: Actor): ApiResponse<DatAuthInfoItem> =
        loginService.loginByPassword(request, actor)

    @PostMapping("/token")
    fun doTokenLogin(@RequestBody request: TokenLoginRequest, actor: Actor): ApiResponse<DatAuthInfoItem> =
        loginService.loginByToken(request, actor)

    @PutMapping
    fun updateAuthInfo(actor: Actor): ApiResponse<DatAuthInfoItem> = loginService.refresh(actor)
}
