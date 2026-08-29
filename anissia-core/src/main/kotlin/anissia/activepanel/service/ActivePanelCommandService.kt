package anissia.activepanel.service

import anissia.account.domain.AccountRole
import anissia.account.repository.AccountRepository
import anissia.activepanel.api.dto.ActivePanelCommandRequest
import anissia.anime.service.AnimeCaptionService
import anissia.anime.service.AnimeDocumentService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class ActivePanelCommandService(
    private val accountRepository: AccountRepository,
    private val animeDocumentService: AnimeDocumentService,
    private val animeCaptionService: AnimeCaptionService,
    private val activePanelLogService: ActivePanelLogService,
) {
    @Transactional
    fun doCommand(request: ActivePanelCommandRequest, actor: Actor): ApiResponse<Unit> {
        request.validate()
        actor.validateAdmin()

        if (!request.isCommand) {
            activePanelLogService.addNotice(request, actor)
            return ApiResponse.ok()
        }

        return when {
            request.query.startsWith("/권한반납 ") -> revokeTranslator(request, actor)
            request.query.startsWith("/차단 ") -> banAccount(request, actor)
            request.query == "/검색엔진 전체갱신" -> reindex(actor, drop = false)
            request.query == "/검색엔진 초기화" -> reindex(actor, drop = true)
            else -> ApiResponse.fail("존재하지 않는 명령입니다.")
        }
    }

    private fun revokeTranslator(request: ActivePanelCommandRequest, actor: Actor): ApiResponse<Unit> {
        actor.validateRoot()

        val name = request.query.substring(request.query.indexOf(' ') + 1)
        val account = accountRepository.findByName(name)
            ?: return ApiResponse.fail("존재하지 않는 회원입니다.")

        if (!account.isTranslator) {
            return ApiResponse.fail("${account.name}님은 자막제작자 권한을 가지고 있지 않습니다.")
        }

        account.roles.removeIf { it == AccountRole.TRANSLATOR }
        accountRepository.save(account)
        val deleteCount = animeCaptionService.deleteAllOf(account, actor)
        activePanelLogService.addText("[${account.name}]님의 자막제작자 권한이 해지되었습니다.", actor = actor)
        activePanelLogService.addText("[${account.name}]님의 모든 작품 ${deleteCount}개가 삭제되었습니다.", actor = actor)

        return ApiResponse.ok()
    }

    private fun banAccount(request: ActivePanelCommandRequest, actor: Actor): ApiResponse<Unit> {
        actor.validateAdmin()

        val tokens = request.query.trim().split(Regex("\\s+"))
        if (tokens.size < 4) {
            return ApiResponse.fail("포멧에 맞게 작성해주세요.\nex) /차단 홍길동 320 광고글 작성")
        }
        val name = tokens[1]
        val day = tokens[2].toIntOrNull() ?: -1
        val reason = tokens.drop(3).joinToString(" ").trim()

        val account = accountRepository.findByName(name)
            ?: return ApiResponse.fail("${name}는 존재하지 않는 유저 입니다.")

        if (day !in 0..999) {
            return ApiResponse.fail("차단일은 0~999일 사이로 입력해주세요.")
        }

        if (reason.isBlank()) {
            return ApiResponse.fail("사유를 작성해주세요.")
        }

        account.banExpireDt = OffsetDateTime.now().plusDays(day.toLong())
        accountRepository.save(account)
        activePanelLogService.addText(
            "[${actor.name}]님이 [${account.name}]님의 계정을 ${day}일간 차단되었습니다.\n사유: $reason",
            actor = actor,
        )

        return ApiResponse.ok()
    }

    private fun reindex(actor: Actor, drop: Boolean): ApiResponse<Unit> {
        actor.validateRoot()

        val action = if (drop) "초기화" else "reindex"
        activePanelLogService.addText("[${actor.name}]님이 검색엔진 $action 작업을 시작했습니다.", actor = actor)
        animeDocumentService.reset(drop)
        activePanelLogService.addText("검색엔진 $action 작업이 완료되었습니다.", actor = actor)

        return ApiResponse.ok()
    }
}
