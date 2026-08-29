package anissia.translator.service

import anissia.account.domain.AccountRole
import anissia.account.repository.AccountRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.agenda.domain.Agenda
import anissia.agenda.domain.AgendaPoll
import anissia.agenda.repository.AgendaPollRepository
import anissia.agenda.repository.AgendaRepository
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.badRequestUnless
import anissia.support.fail
import anissia.translator.api.dto.AddApplyRequest
import anissia.translator.api.dto.NewApplyPollRequest
import anissia.translator.api.dto.TranslatorApplyItem
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class TranslatorApplyService(
    private val agendaRepository: AgendaRepository,
    private val agendaPollRepository: AgendaPollRepository,
    private val accountRepository: AccountRepository,
    private val activePanelLogService: ActivePanelLogService,
) {
    @Transactional(readOnly = true)
    fun get(applyNo: Long): TranslatorApplyItem {
        badRequestUnless(applyNo > 0) { "잘못된 번호" }
        return agendaRepository.findWithPollsByAgendaNoAndCode(applyNo, CODE)
            ?.let { TranslatorApplyItem(it, true) }
            ?: TranslatorApplyItem()
    }

    @Transactional(readOnly = true)
    fun getList(page: Int): Page<TranslatorApplyItem> {
        badRequestUnless(page >= 0) { "잘못된 페이지" }
        return agendaRepository.findAllByCodeOrderByStatusAscAgendaNoDesc(CODE, PageRequest.of(page, 30))
            .map { TranslatorApplyItem(it) }
    }

    @Transactional(readOnly = true)
    fun getApplyingCount(): Int = agendaRepository.countByCodeAndStatus(CODE, "ACT")

    @Transactional(readOnly = true)
    fun grantedAt(an: Long): OffsetDateTime? =
        agendaRepository.findPassedTranslatorApply(an).firstOrNull()?.updDt

    fun isGrantedBefore90Days(an: Long): Boolean =
        grantedAt(an)?.isBefore(OffsetDateTime.now().minusDays(90)) == true

    @Transactional(readOnly = true)
    fun isApplying(actor: Actor): Boolean =
        agendaRepository.existsByCodeAndStatusAndAn(CODE, "ACT", actor.an)

    @Transactional
    fun add(request: AddApplyRequest, actor: Actor): ApiResponse<Long> {
        request.validate()
        actor.validateLogin()

        if (actor.isAdmin) {
            fail("이미 권한이 있습니다.")
        }

        if (isApplying(actor)) {
            fail("신청중인 진행사항이 있습니다.")
        }

        if (
            agendaRepository.existsByCodeAndStatusAndAnAndUpdDtAfter(
                CODE,
                "DONE",
                actor.an,
                OffsetDateTime.now().minusDays(7),
            )
        ) {
            fail("심사완료 일주일 후부터 재심사를 요청할 수 있습니다.")
        }

        val agenda = agendaRepository.saveAndFlush(
            Agenda(
                code = CODE,
                status = "ACT",
                an = actor.an,
                data1 = "ACT",
                data2 = actor.name,
                data3 = request.website,
            ),
        )
        return ApiResponse.ok(agenda.agendaNo)
    }

    @Transactional
    fun addPoll(applyNo: Long, request: NewApplyPollRequest, actor: Actor): ApiResponse<Unit> {
        request.validate(applyNo)
        actor.validateLogin()

        var point = request.point.toInt()

        val apply = agendaRepository.findByIdOrNull(applyNo)?.takeIf { it.code == CODE }
            ?: return ApiResponse.fail("존재하지 않는 신청입니다.")

        apply.takeIf { it.status == "ACT" }
            ?: return ApiResponse.fail("종료된 신청서입니다.")

        if (!actor.isAdmin) {
            if (apply.an != actor.an) {
                return ApiResponse.fail("권한이 없습니다.")
            }
            if (point != 0) {
                return ApiResponse.fail("신청자는 의견만 작성할 수 있습니다.")
            }
        }

        val polls = apply.polls
        if (point != 0 && polls.filter { it.an == actor.an }.any { it.vote != 0 }) {
            return ApiResponse.fail("찬성/반대는 한 신청처에 한번만 할 수 있습니다.")
        }

        if (actor.isRoot) {
            point *= 10
        }

        val poll = agendaPollRepository.save(
            AgendaPoll(
                agenda = apply,
                voteUp = if (point > 0) point else 0,
                voteDown = if (point < 0) point else 0,
                name = actor.name,
                an = actor.an,
                comment = request.comment,
            ),
        )

        val vote = (polls + poll).sumOf { it.vote }
        if (vote >= 3) {
            apply.status = "DONE"
            apply.data1 = "PASS"
            val account = accountRepository.findByIdOrNull(apply.an)!!
            account.roles.add(AccountRole.TRANSLATOR)
            accountRepository.save(account)
            activePanelLogService.addText("[${account.name}]님이 자막제작자로 참여하였습니다.")
            agendaPollRepository.save(systemPoll(apply, "조건이 충족되어 권한이 부여되었습니다."))
        } else if (vote <= -3) {
            apply.status = "DONE"
            apply.data1 = "FAIL"
            agendaPollRepository.save(systemPoll(apply, "최종 반려되었습니다. (7일 후 재신청이 가능합니다.)"))
        }
        agendaRepository.save(apply.apply { updDt = OffsetDateTime.now() })

        return ApiResponse.ok()
    }

    private fun systemPoll(agenda: Agenda, comment: String) = AgendaPoll(
        agenda = agenda,
        name = "",
        an = 0,
        comment = comment,
    )

    companion object {
        const val CODE = "TRANSLATOR-APPLY"
    }
}
