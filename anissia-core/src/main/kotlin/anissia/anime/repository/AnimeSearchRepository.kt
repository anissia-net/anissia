package anissia.anime.repository

import anissia.search.ContentSearchRepository
import anissia.search.SearchKind
import org.springframework.stereotype.Repository

class AnimeSearchResult(val animeNos: List<Long>, val totalHits: Long)

@Repository
class AnimeSearchRepository(private val search: ContentSearchRepository) {
    fun search(query: String, page: Int): AnimeSearchResult = search.search(SearchKind.ANIME, query, page, 30).let {
        AnimeSearchResult(it.ids.map(String::toLong), it.total)
    }
}
