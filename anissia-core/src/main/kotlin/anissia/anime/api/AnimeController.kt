package anissia.anime.api

import anissia.anime.api.dto.AnimeItem
import anissia.anime.api.dto.EditAnimeRequest
import anissia.anime.api.dto.NewAnimeRequest
import anissia.anime.service.AnimeGenreService
import anissia.anime.service.AnimeRankService
import anissia.anime.service.AnimeService
import anissia.security.Actor
import anissia.support.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/anime")
class AnimeController(
    private val animeService: AnimeService,
    private val animeGenreService: AnimeGenreService,
    private val animeRankService: AnimeRankService,
) {
    @GetMapping("/list/{page:\\d+}")
    fun getAnimeList(
        @PathVariable page: Int,
        @RequestParam(defaultValue = "") q: String,
    ): ApiResponse<Page<AnimeItem>> = ApiResponse.ok(animeService.getList(q, page))

    @GetMapping("/delist")
    fun getAnimeDelist(actor: Actor): ApiResponse<Page<AnimeItem>> = ApiResponse.ok(animeService.getDelist(actor))

    @GetMapping("/animeNo/{animeNo:\\d+}")
    fun getAnime(@PathVariable animeNo: Long, actor: Actor): ApiResponse<AnimeItem> =
        ApiResponse.ok(animeService.get(animeNo, actor))

    @GetMapping("/autocorrect")
    fun getAnimeAutocorrect(@RequestParam(defaultValue = "") q: String): ApiResponse<List<String>> =
        ApiResponse.ok(animeService.getAutocorrect(q))

    @GetMapping("/genres")
    fun getGenres(): ApiResponse<List<String>> = ApiResponse.ok(animeGenreService.getAll())

    @GetMapping("/rank/{type}")
    fun getAnimeRank(@PathVariable type: String): ApiResponse<List<Map<*, *>>> =
        ApiResponse.ok(animeRankService.get(type))

    @DeleteMapping("/{animeNo}")
    fun deleteAnime(@PathVariable animeNo: Long, actor: Actor): ApiResponse<Unit> =
        animeService.delete(animeNo, actor)

    @PostMapping
    fun newAnime(@RequestBody request: NewAnimeRequest, actor: Actor): ApiResponse<Long> =
        animeService.add(request, actor)

    @PutMapping("/{animeNo}")
    fun editAnime(
        @PathVariable animeNo: Long,
        @RequestBody request: EditAnimeRequest,
        actor: Actor,
    ): ApiResponse<Long> = animeService.edit(animeNo, request, actor)

    @PostMapping("/recover/{agendaNo}")
    fun recoverAnime(@PathVariable agendaNo: Long, actor: Actor): ApiResponse<Long> =
        animeService.recover(agendaNo, actor)
}
