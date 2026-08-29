package anissia.account.api

import anissia.account.api.dto.AccountUserItem
import anissia.account.api.dto.EditUserNameRequest
import anissia.account.api.dto.EditUserPasswordRequest
import anissia.account.api.dto.WithdrawRequest
import anissia.account.service.AccountService
import anissia.account.service.AccountWithdrawService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/account/user")
class AccountUserController(
    private val accountService: AccountService,
    private val accountWithdrawService: AccountWithdrawService,
) {
    @GetMapping
    fun getUser(actor: Actor): AccountUserItem = accountService.get(actor)

    @PutMapping("/password")
    fun editUserPassword(@RequestBody request: EditUserPasswordRequest, actor: Actor): ApiResponse<Unit> =
        accountService.editPassword(request, actor)

    @PutMapping("/name")
    fun editUserName(@RequestBody request: EditUserNameRequest, actor: Actor): ApiResponse<Unit> =
        accountService.editName(request, actor)

    @DeleteMapping
    fun withdraw(@RequestBody request: WithdrawRequest, actor: Actor): ApiResponse<Unit> =
        accountWithdrawService.withdraw(request, actor)
}
