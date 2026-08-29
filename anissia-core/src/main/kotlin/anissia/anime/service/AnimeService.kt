package anissia.anime.service

import anissia.account.repository.AccountRepository
import anissia.activepanel.domain.ActivePanel
import anissia.activepanel.repository.ActivePanelRepository
import anissia.activepanel.service.ActivePanelLogService
import anissia.agenda.domain.Agenda
import anissia.agenda.repository.AgendaRepository
import anissia.anime.api.dto.AnimeItem
import anissia.anime.api.dto.EditAnimeRequest
import anissia.anime.api.dto.NewAnimeRequest
import anissia.anime.domain.Anime
import anissia.anime.domain.AnimeCaption
import anissia.anime.domain.AnimeStatus
import anissia.anime.repository.AnimeCaptionRepository
import anissia.anime.repository.AnimeGenreRepository
import anissia.anime.repository.AnimeRepository
import anissia.anime.repository.AnimeSearchRepository
import anissia.anime.repository.jooq.AnimeQueryRepository
import anissia.counter.DerivedCounters
import anissia.security.Actor
import anissia.translator.service.TranslatorApplyService
import anissia.support.ApiResponse
import anissia.support.DateFormats
import anissia.support.Json
import anissia.support.getOrLoad
import anissia.support.logger
import anissia.support.replaceContent
import anissia.support.ttlCache
import me.saro.kit.lang.KoreanKit
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

@Service
class AnimeService(
    private val animeRepository: AnimeRepository,
    private val animeCaptionRepository: AnimeCaptionRepository,
    private val animeGenreRepository: AnimeGenreRepository,
    private val animeQueryRepository: AnimeQueryRepository,
    private val derivedCounters: DerivedCounters,
    private val animeSearchRepository: AnimeSearchRepository,
    private val animeRankService: AnimeRankService,
    private val activePanelLogService: ActivePanelLogService,
    private val activePanelRepository: ActivePanelRepository,
    private val agendaRepository: AgendaRepository,
    private val accountRepository: AccountRepository,
    private val translatorApplyService: TranslatorApplyService,
    private val events: ApplicationEventPublisher,
) {
    private val log = logger<AnimeService>()
    private val autocorrectCache = ttlCache<String, List<String>>(60.minutes)

    @Transactional(readOnly = true)
    fun get(animeNo: Long, actor: Actor): AnimeItem =
        animeRepository.findWithCaptionsByAnimeNo(animeNo)
            ?.let { AnimeItem(it, true) }
            ?.also { animeRankService.hit(animeNo, actor) }
            ?: AnimeItem()

    @Transactional(readOnly = true)
    fun getList(q: String, page: Int): Page<AnimeItem> {
        if (q.isBlank()) {
            return animeRepository.findAllByOrderByAnimeNoDesc(PageRequest.of(page, 30)).map { AnimeItem(it) }
        }

        val found = animeSearchRepository.search(q, page)
        val hitPage = PageImpl(found.animeNos, PageRequest.of(page, 30), found.totalHits)

        log.info("anime search {}: {} items", q, found.totalHits)

        if (found.animeNos.isEmpty()) {
            return hitPage.replaceContent(emptyList())
        }
        return hitPage.replaceContent(
            animeRepository.findAllByAnimeNoInOrderByAnimeNoDesc(found.animeNos).map { AnimeItem(it) },
        )
    }

    @Transactional(readOnly = true)
    fun getDelist(actor: Actor): Page<AnimeItem> {
        actor.validateAdmin()

        return agendaRepository.findAllByCodeAndStatusOrderByAgendaNoDesc(CODE_ANIME_DEL, "wait")
            .map { Json.read<AnimeItem>(it.data1!!).apply { agendaNo = it.agendaNo } }
    }

    @Transactional(readOnly = true)
    fun getAutocorrect(q: String): List<String> =
        if (q.length < 3) autocorrectCache.getOrLoad(q) { loadAutocorrect(it) } else loadAutocorrect(q)

    private fun loadAutocorrect(q: String): List<String> =
        q.replace("%", "").trim()
            .takeIf { it.isNotEmpty() }
            ?.let { animeQueryRepository.findAutocorrectTop10(KoreanKit.toJasoAtom(it)) }
            ?: listOf()

    @Transactional
    fun add(request: NewAnimeRequest, actor: Actor): ApiResponse<Long> {
        request.validate()
        actor.validateAdmin()
        if (!translatorApplyService.isGrantedBefore90Days(actor.an)) {
            return ApiResponse.fail("애니메이션 등록은 권한 취득일로부터 90일 후에 가능합니다.", -1)
        }

        if (animeGenreRepository.countByGenreIn(request.genresList).toInt() != request.genresList.size) {
            return ApiResponse.fail("장르 입력이 잘못되었습니다.", -1)
        }

        if (animeRepository.existsBySubject(request.subject)) {
            return ApiResponse.fail("이미 동일한 이름의 작품이 존재합니다.", -1)
        }

        val anime = Anime(
            status = request.statusEnum,
            week = request.week,
            time = request.time,
            subject = request.subject,
            originalSubject = request.originalSubject,
            autocorrect = KoreanKit.toJasoAtom(request.subject),
            genres = request.genres,
            startDate = request.startDate,
            endDate = request.endDate,
            website = request.website,
            x = request.x,
        )

        animeRepository.save(anime)
        activePanelRepository.save(
            ActivePanel(
                published = true,
                code = CODE_ANIME,
                status = "C",
                an = actor.an,
                data1 = "[${actor.name}]님이 애니메이션 [${anime.subject}]을(를) 추가하였습니다.",
            ),
        )
        events.publishEvent(AnimeDocumentChangedEvent(anime.animeNo))

        return ApiResponse.ok(anime.animeNo)
    }

    @Transactional
    fun edit(animeNo: Long, request: EditAnimeRequest, actor: Actor): ApiResponse<Long> {
        request.validate(animeNo)
        actor.validateAdmin()
        if (!translatorApplyService.isGrantedBefore90Days(actor.an)) {
            return ApiResponse.fail("애니메이션 편집은 권한 취득일로부터 90일 후에 가능합니다.", -1)
        }

        if (animeGenreRepository.countByGenreIn(request.genresList).toInt() != request.genresList.size) {
            return ApiResponse.fail("장르 입력이 잘못되었습니다.", -1)
        }

        if (animeRepository.existsBySubjectAndAnimeNoNot(request.subject, animeNo)) {
            return ApiResponse.fail("이미 동일한 이름의 작품이 존재합니다.", -1)
        }

        val activePanel = ActivePanel(
            published = true,
            code = CODE_ANIME,
            status = "U",
            an = actor.an,
            data1 = "[${actor.name}]님이 애니메이션 [${request.subject}]을(를) 수정하였습니다.",
        )

        val anime = animeRepository.findByIdOrNull(animeNo)
            ?.also {
                if (
                    it.week == request.week &&
                    it.status == request.statusEnum &&
                    it.time == request.time &&
                    it.subject == request.subject &&
                    it.originalSubject == request.originalSubject &&
                    it.genres == request.genres &&
                    it.startDate == request.startDate &&
                    it.endDate == request.endDate &&
                    it.website == request.website &&
                    it.x == request.x &&
                    it.note == request.note
                ) {
                    return ApiResponse.fail("변경사항이 없습니다.", -1)
                }
            }
            ?.also { activePanel.data2 = Json.write(AnimeItem(it, false)) }
            ?.apply {
                status = request.statusEnum
                week = request.week
                time = request.time
                subject = request.subject
                originalSubject = request.originalSubject
                autocorrect = KoreanKit.toJasoAtom(request.subject)
                genres = request.genres
                startDate = request.startDate
                endDate = request.endDate
                website = request.website
                x = request.x
                note = request.note
            }
            ?.also { activePanel.data3 = Json.write(AnimeItem(it, false)) }
            ?: return ApiResponse.fail("존재하지 않는 애니메이션입니다.", -1)

        animeRepository.save(anime)
        activePanelRepository.save(activePanel)
        events.publishEvent(AnimeDocumentChangedEvent(anime.animeNo))

        return ApiResponse.of("ok", "", anime.animeNo)
    }

    @Transactional
    fun delete(animeNo: Long, actor: Actor): ApiResponse<Unit> {
        require(animeNo > 0) { "animeNo 는 0 이상이어야 합니다." }
        actor.validateAdmin()
        if (!translatorApplyService.isGrantedBefore90Days(actor.an)) {
            return ApiResponse.fail("애니메이션 삭제는 권한 취득일로부터 90일 후에 가능합니다.")
        }

        val agenda = Agenda(code = CODE_ANIME_DEL, status = "wait", an = actor.an)

        val anime = animeRepository.findWithCaptionsByAnimeNo(animeNo)
            ?.also { agenda.data1 = Json.write(AnimeItem(it, true)) }
            ?: return ApiResponse.fail("존재하지 않는 애니메이션입니다.")

        activePanelLogService.addText("[${actor.name}]님이 애니메이션 [${anime.subject}]을(를) 삭제하였습니다.")

        animeCaptionRepository.deleteByAnimeNo(animeNo)
        animeRepository.delete(anime)
        agendaRepository.save(agenda)
        events.publishEvent(AnimeDocumentChangedEvent(animeNo))

        return ApiResponse.ok()
    }

    @Transactional
    fun recover(agendaNo: Long, actor: Actor): ApiResponse<Long> {
        require(agendaNo > 0) { "agendaNo 는 0 이상이어야 합니다." }
        actor.validateAdmin()

        val agenda = agendaRepository.findByIdOrNull(agendaNo)
            ?.takeIf { it.code == CODE_ANIME_DEL && it.status == "wait" }
            ?: return ApiResponse.fail("이미 복원되었거나 존재하지 않는 애니메이션입니다.", -1)

        val animeItem = Json.read<AnimeItem>(agenda.data1!!)

        if (animeRepository.existsById(animeItem.animeNo)) {
            return ApiResponse.fail("이미 복원되었거나 존재하지 않는 애니메이션입니다.", -1)
        }
        if (animeRepository.existsBySubject(animeItem.subject)) {
            return ApiResponse.fail("이미 해당 제목의 에니메이션이 있습니다.", -1)
        }

        val anime = animeRepository.save(
            Anime(
                status = AnimeStatus.valueOf(animeItem.status),
                week = animeItem.week,
                time = animeItem.time,
                subject = animeItem.subject,
                originalSubject = animeItem.originalSubject,
                autocorrect = KoreanKit.toJasoAtom(animeItem.subject),
                genres = animeItem.genres,
                startDate = animeItem.startDate,
                endDate = animeItem.endDate,
                website = animeItem.website,
                x = animeItem.x,
                captionCount = animeItem.captionCount,
            ),
        )

        animeItem.captions.forEach { caption ->
            val account = accountRepository.findWithRolesByName(caption.name)
            if (account?.isAdmin == true) {
                animeCaptionRepository.save(
                    AnimeCaption(
                        anime = anime,
                        an = account.an,
                        episode = caption.episode,
                        updDt = LocalDateTime.parse(caption.updDt, DateFormats.ISO_YMDHMS)
                            .atOffset(DateFormats.KST),
                        website = caption.website,
                    ),
                )
            }
        }

        activePanelLogService.addText("[${actor.name}]님이 애니메이션 [${anime.subject}]을(를) 복원하였습니다.")

        derivedCounters.markAnime(anime.animeNo)
        agendaRepository.save(agenda.apply { status = "recover" })
        events.publishEvent(AnimeDocumentChangedEvent(anime.animeNo))

        return ApiResponse.of("ok", "", anime.animeNo)
    }

    companion object {
        const val CODE_ANIME = "ANIME"
        const val CODE_ANIME_DEL = "ANIME-DEL"
    }
}
