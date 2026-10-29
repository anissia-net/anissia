package anissia.anime.service

class AnimeDocumentChangedEvent(val animeNos: List<Long>) {
    constructor(animeNo: Long) : this(listOf(animeNo))
}
