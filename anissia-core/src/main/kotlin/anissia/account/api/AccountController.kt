package anissia.account.api

import anissia.account.api.dto.CompleteRecoverPasswordRequest
import anissia.account.api.dto.RecoverPasswordRequest
import anissia.account.api.dto.RegisterRequest
import anissia.account.api.dto.TokenOnlyRequest
import anissia.account.service.PasswordRecoveryService
import anissia.account.service.RegistrationService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/account")
class AccountController(
    private val registrationService: RegistrationService,
    private val passwordRecoveryService: PasswordRecoveryService,
) {
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest, actor: Actor): ApiResponse<Unit> =
        registrationService.request(request, actor)

    @PutMapping("/register")
    fun registerValidation(@RequestBody request: TokenOnlyRequest): ApiResponse<Unit> =
        registrationService.complete(request)

    @PostMapping("/recover")
    fun recover(@RequestBody request: RecoverPasswordRequest, actor: Actor): ApiResponse<Unit> =
        passwordRecoveryService.request(request, actor)

    @PutMapping("/recover")
    fun recoverValidation(@RequestBody request: TokenOnlyRequest): ApiResponse<Unit> =
        passwordRecoveryService.validate(request)

    @PutMapping("/recover/password")
    fun recoverPassword(@RequestBody request: CompleteRecoverPasswordRequest): ApiResponse<Unit> =
        passwordRecoveryService.complete(request)
}
