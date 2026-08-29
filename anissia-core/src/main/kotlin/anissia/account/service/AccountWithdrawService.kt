package anissia.account.service

import anissia.account.api.dto.WithdrawRequest
import anissia.account.repository.AccountRecoverAuthRepository
import anissia.account.repository.AccountRepository
import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.anime.repository.AnimeCaptionRepository
import anissia.anime.service.AnimeDocumentChangedEvent
import anissia.board.repository.BoardPostRepository
import anissia.board.repository.BoardTopicRepository
import anissia.counter.DerivedCounters
import anissia.security.Actor
import anissia.security.PasswordHasher
import anissia.session.repository.LoginFailRepository
import anissia.session.repository.LoginPassRepository
import anissia.session.repository.LoginTokenRepository
import anissia.support.ApiResponse
import anissia.support.logger
import anissia.translator.service.TranslatorApplyService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccountWithdrawService(
    private val passwordHasher: PasswordHasher,
    private val accountRepository: AccountRepository,
    private val accountRecoverAuthRepository: AccountRecoverAuthRepository,
    private val boardTopicRepository: BoardTopicRepository,
    private val boardPostRepository: BoardPostRepository,
    private val animeCaptionRepository: AnimeCaptionRepository,
    private val derivedCounters: DerivedCounters,
    private val loginTokenRepository: LoginTokenRepository,
    private val loginPassRepository: LoginPassRepository,
    private val loginFailRepository: LoginFailRepository,
    private val activePanelRepository: ActivePanelRepository,
    private val translatorApplyService: TranslatorApplyService,
    private val events: ApplicationEventPublisher,
) {
    private val log = logger<AccountWithdrawService>()

    @Transactional
    fun withdraw(request: WithdrawRequest, actor: Actor): ApiResponse<Unit> {
        request.validate()
        actor.validateLogin()

        val account = accountRepository.findWithRolesByAn(actor.an)
            ?: return ApiResponse.fail("존재하지 않는 계정입니다.")

        if (!passwordHasher.matches(request.password, account.password)) {
            return ApiResponse.fail("암호가 일치하지 않습니다.")
        }

        if (account.roles.isNotEmpty()) {
            return ApiResponse.fail("권한이 있는 계정은 탈퇴할 수 없습니다.\n운영진에게 문의해주세요.")
        }

        if (translatorApplyService.isApplying(actor)) {
            return ApiResponse.fail("자막제작자 신청중에는 탈퇴할 수 없습니다.\n심사가 끝난 후 다시 시도해주세요.")
        }

        val an = account.an
        val email = account.email
        val name = account.name

        val ownTopicNos = boardTopicRepository.findTopicNosByAn(an)
        val otherTopicNos = boardPostRepository.findTopicNosByPostAn(an).filterNot { it in ownTopicNos }

        if (ownTopicNos.isNotEmpty()) {
            boardPostRepository.deleteAllByTopicNoIn(ownTopicNos)
        }
        boardPostRepository.deleteAllByAn(an)
        boardTopicRepository.deleteAllByAn(an)
        derivedCounters.markTopics(otherTopicNos)

        val animeNos = animeCaptionRepository.findAnimeNosByAn(an)
        if (animeNos.isNotEmpty()) {
            animeCaptionRepository.deleteAllByAn(an)
            derivedCounters.markAnimes(animeNos)
            events.publishEvent(AnimeDocumentChangedEvent(animeNos))
        }

        accountRecoverAuthRepository.deleteAllByAn(an)
        loginTokenRepository.deleteAllByAn(an)
        loginPassRepository.deleteAllByAn(an)
        loginFailRepository.deleteAllByEmail(email)

        accountRepository.delete(account)

        activePanelRepository.save(
            ActivePanel(
                published = false,
                code = "WITHDRAW",
                an = an,
                data1 = "[$name]님이 회원 탈퇴하였습니다.",
                data2 = "회원번호: $an",
                data3 = "삭제된 글 ${ownTopicNos.size}개 / 댓글 정리된 글 ${otherTopicNos.size}개",
            ),
        )

        log.info("withdraw an={} topics={} otherTopics={}", an, ownTopicNos.size, otherTopicNos.size)

        return ApiResponse.ok()
    }
}
