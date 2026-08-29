package anissia.anime.service

import anissia.account.domain.Account
import anissia.activepanel.service.ActivePanelLogService
import anissia.anime.api.dto.CaptionItem
import anissia.anime.api.dto.CaptionRecentItem
import anissia.anime.api.dto.EditCaptionRequest
import anissia.anime.api.dto.MyCaptionItem
import anissia.anime.domain.AnimeCaption
import anissia.anime.repository.AnimeCaptionRepository
import anissia.anime.repository.AnimeRepository
import anissia.counter.DerivedCounters
import anissia.security.Actor
import anissia.support.ApiResponse
import anissia.support.DateFormats
import anissia.support.badRequestUnless
import anissia.support.getOrLoad
import anissia.support.ttlCache
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.Duration.Companion.minutes

@Service
class AnimeCaptionService(
    private val animeCaptionRepository: AnimeCaptionRepository,
    private val animeRepository: AnimeRepository,
    private val derivedCounters: DerivedCounters,
    private val activePanelLogService: ActivePanelLogService,
    private val animeRankService: AnimeRankService,
    private val events: ApplicationEventPublisher,
) {
    private val recentCache = ttlCache<Int, Page<CaptionRecentItem>>(5.minutes)

    @Transactional(readOnly = true)
    fun getListByAnimeNo(animeNo: Long, actor: Actor): List<CaptionItem> {
        badRequestUnless(animeNo > 0) { "animeNo 는 0 이상이어야 합니다." }
        return animeCaptionRepository.findAllWithAccountByAnimeNo(animeNo)
            .map { CaptionItem(it) }
            .also { animeRankService.hit(animeNo, actor) }
    }

    @Transactional(readOnly = true)
    fun getMyList(active: Int, page: Int, actor: Actor): Page<MyCaptionItem> {
        badRequestUnless(active in 0..1) { "active 는 0 또는 1 이어야 합니다." }
        badRequestUnless(page >= 0) { "page 는 0 이상이어야 합니다." }
        actor.validateAdmin()

        val pageable = PageRequest.of(page, 20)
        return if (active == 1) {
            animeCaptionRepository.findAllActiveWithAnimeByAn(actor.an, pageable)
        } else {
            animeCaptionRepository.findAllEndedWithAnimeByAn(actor.an, pageable)
        }.map { MyCaptionItem(it) }
    }

    @Transactional(readOnly = true)
    fun getRecent(page: Int): Page<CaptionRecentItem> {
        badRequestUnless(page > -2) { "잘못된 pageNo" }

        return if (page == -1) {
            recentCache.getOrLoad(page) { loadRecent(PageRequest.of(0, 12)) }
        } else {
            loadRecent(PageRequest.of(page, 20))
        }
    }

    private fun loadRecent(pageable: PageRequest): Page<CaptionRecentItem> =
        animeCaptionRepository.findAllByUpdDtAfterAndWebsiteNotOrderByUpdDtDesc(pageable)
            .map { CaptionRecentItem(it) }

    @Transactional
    fun add(animeNo: Long, actor: Actor): ApiResponse<Unit> {
        badRequestUnless(animeNo > 0) { "animeNo 는 0 이상이어야 합니다." }
        actor.validateAdmin()

        val anime = animeRepository.findByIdOrNull(animeNo)
            ?: return ApiResponse.fail("존재하지 않는 애니메이션입니다.")

        if (animeCaptionRepository.findById(AnimeCaption.Key(animeNo, actor.an)).isPresent) {
            return ApiResponse.fail("이미 작업중인 작품입니다.")
        }

        animeCaptionRepository.save(AnimeCaption(anime = anime, an = actor.an))
        derivedCounters.markAnime(animeNo)
        activePanelLogService.addText("[${actor.name}]님이 [${anime.subject}] 자막을 시작하였습니다.", true)
        events.publishEvent(AnimeDocumentChangedEvent(animeNo))

        return ApiResponse.of("ok", "자막을 추가하였습니다.\n자막메뉴에서 확인해주세요.")
    }

    @Transactional
    fun edit(animeNo: Long, request: EditCaptionRequest, actor: Actor): ApiResponse<Unit> {
        request.validate(animeNo)
        actor.validateAdmin()

        val caption = animeCaptionRepository.findByIdOrNull(AnimeCaption.Key(animeNo, actor.an))
            ?: return ApiResponse.fail("존재하지 않는 자막입니다.")

        animeCaptionRepository.save(
            caption.apply {
                edit(
                    episode = request.episode,
                    updDt = request.updatedAt.atOffset(DateFormats.KST),
                    website = request.website,
                )
            },
        )

        return ApiResponse.of("ok", "자막정보가 반영되었습니다.")
    }

    @Transactional
    fun delete(animeNo: Long, actor: Actor): ApiResponse<Unit> {
        badRequestUnless(animeNo > 0) { "animeNo 는 0 이상이어야 합니다." }
        actor.validateAdmin()

        val caption = animeCaptionRepository.findByIdOrNull(AnimeCaption.Key(animeNo, actor.an))
            ?: return ApiResponse.fail("이미 삭제되었습니다.")

        val subject = caption.anime?.subject
        animeCaptionRepository.delete(caption)
        derivedCounters.markAnime(animeNo)
        activePanelLogService.addText("[${actor.name}]님이 [$subject] 자막을 종료하였습니다.", true)
        events.publishEvent(AnimeDocumentChangedEvent(animeNo))

        return ApiResponse.ok()
    }

    @Transactional
    fun deleteAllOf(account: Account, actor: Actor): Int {
        actor.validateRoot()

        val captions = animeCaptionRepository.findAllByAn(account.an)
        val animeNos = captions.mapNotNull { it.anime?.animeNo }

        animeCaptionRepository.deleteAll(captions)
        derivedCounters.markAnimes(animeNos)
        events.publishEvent(AnimeDocumentChangedEvent(animeNos))

        return captions.count()
    }
}
