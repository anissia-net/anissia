package anissia.account.service

import anissia.account.api.dto.AccountUserItem
import anissia.account.api.dto.EditUserNameRequest
import anissia.account.api.dto.EditUserPasswordRequest
import anissia.account.repository.AccountBanNameRepository
import anissia.account.repository.AccountRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.agenda.domain.Agenda
import anissia.agenda.repository.AgendaRepository
import anissia.anime.repository.AnimeCaptionRepository
import anissia.anime.service.AnimeDocumentChangedEvent
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.FailException
import anissia.support.Texts
import anissia.support.fail
import anissia.translator.service.TranslatorApplyService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import anissia.security.PasswordHasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class AccountService(
    private val passwordHasher: PasswordHasher,
    private val accountRepository: AccountRepository,
    private val accountBanNameRepository: AccountBanNameRepository,
    private val translatorApplyService: TranslatorApplyService,
    private val agendaRepository: AgendaRepository,
    private val activePanelLogService: ActivePanelLogService,
    private val animeCaptionRepository: AnimeCaptionRepository,
    private val events: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun get(actor: Actor): AccountUserItem =
        AccountUserItem.of(accountRepository.findWithRolesByAn(actor.an)!!)

    @Transactional(readOnly = true)
    fun validateCriticalActor(actor: Actor) {
        actor.validateLogin()
        accountRepository.findWithRolesByAn(actor.an)?.takeIf { account ->
            !account.isBan &&
                account.name == actor.name &&
                account.email == actor.email &&
                Texts.sameElements(account.roles.map { it.name }, actor.roles)
        } ?: throw SESSION_EXPIRED
    }

    @Transactional
    fun editPassword(request: EditUserPasswordRequest, actor: Actor): ApiResponse<Unit> {
        request.validate()
        actor.validateLogin()

        val account = accountRepository.findByIdOrNull(actor.an)
            ?.takeIf { passwordHasher.matches(request.oldPassword, it.password) }
            ?: return ApiResponse.fail("기존 암호가 일치하지 않습니다.")

        account.password = passwordHasher.hash(request.newPassword)
        accountRepository.save(account)
        return ApiResponse.ok()
    }

    @Transactional
    fun editName(request: EditUserNameRequest, actor: Actor): ApiResponse<Unit> {
        request.validate()
        actor.validateLogin()

        val account = accountRepository.findByIdOrNull(actor.an)
            ?.takeIf { passwordHasher.matches(request.password, it.password) }
            ?: return ApiResponse.fail("암호가 일치하지 않습니다.")

        val oldName = account.name
        val newName = request.name

        if (oldName == newName) {
            return ApiResponse.fail("기존 이름과 같습니다.")
        }

        if (translatorApplyService.isApplying(actor)) {
            fail("자막제작자 신청중에는 이름을 바꿀 수 없습니다.")
        }

        if (
            agendaRepository.existsByCodeAndStatusAndAnAndUpdDtAfter(
                CODE_UPDATE_NAME,
                "DONE",
                actor.an,
                OffsetDateTime.now().minusDays(1),
            )
        ) {
            return ApiResponse.fail("이름은 하루에 한번만 바꿀 수 있습니다.")
        }

        if (accountRepository.existsByName(newName) || accountBanNameRepository.existsById(newName)) {
            return ApiResponse.fail("사용중이거나 사용할 수 없는 이름입니다.")
        }

        agendaRepository.saveAndFlush(
            Agenda(
                code = CODE_UPDATE_NAME,
                status = "DONE",
                an = actor.an,
                data1 = "DONE",
                data2 = oldName,
                data3 = newName,
            ),
        )

        accountRepository.saveAndFlush(account.apply { name = newName })

        if (account.roles.isNotEmpty()) {
            val animeNos = animeCaptionRepository.findAllByAn(actor.an).mapNotNull { it.anime?.animeNo }
            events.publishEvent(AnimeDocumentChangedEvent(animeNos))
            activePanelLogService.addText("운영진 [$oldName]님의 닉네임이 [$newName]님으로 변경되었습니다.")
        }

        return ApiResponse.ok()
    }

    companion object {
        private const val CODE_UPDATE_NAME = "AC-UPD-NAME"
        private val SESSION_EXPIRED = FailException("세션정보가 만료되었습니다.\n다시 로그인해주세요.")
    }
}
