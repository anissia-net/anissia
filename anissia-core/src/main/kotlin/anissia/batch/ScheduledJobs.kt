package anissia.batch

import anissia.account.repository.AccountRecoverAuthRepository
import anissia.account.repository.AccountRegisterAuthRepository
import anissia.activepanel.repository.ActivePanelRepository
import anissia.agenda.service.AgendaService
import anissia.anime.service.AnimeRankService
import anissia.counter.DerivedCounterReconciler
import anissia.session.repository.LoginFailRepository
import anissia.session.repository.LoginPassRepository
import anissia.session.repository.LoginTokenRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledJobs(
    private val animeRankService: AnimeRankService,
    private val agendaService: AgendaService,
    private val derivedCounterReconciler: DerivedCounterReconciler,
    private val activePanelRepository: ActivePanelRepository,
    private val loginPassRepository: LoginPassRepository,
    private val loginFailRepository: LoginFailRepository,
    private val loginTokenRepository: LoginTokenRepository,
    private val accountRecoverAuthRepository: AccountRecoverAuthRepository,
    private val accountRegisterAuthRepository: AccountRegisterAuthRepository,
) {
    @Scheduled(cron = "0 1 * * * ?")
    fun animeRankBatch() = animeRankService.renew()

    @Scheduled(cron = "0 0 4 * * ?")
    fun reconcileDerivedCounters() = derivedCounterReconciler.reconcileAll()

    @Scheduled(cron = "0 0 20 * * ?")
    fun deletePaddingDeleteAnime() = agendaService.deleteDeletePaddingAnime()

    @Scheduled(cron = "0 0 10 * * ?")
    fun deleteOldActivePanelList() = activePanelRepository.deleteAllByRegDtBefore()

    @Scheduled(cron = "0 30 10 * * ?")
    fun deleteOldLoginHistory() {
        loginPassRepository.deleteAllByPassDtBefore()
        loginFailRepository.deleteAllByFailDtBefore()
        loginTokenRepository.deleteAllByExpDtBefore()
        accountRecoverAuthRepository.deleteAllByExpDtBefore()
        accountRegisterAuthRepository.deleteAllByExpDtBefore()
    }
}
