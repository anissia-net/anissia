package anissia.external.search

class SearchResponse(
    val status: Int,
    val body: String,
) {
    val isOk: Boolean get() = status == 200
}

interface SearchClient {
    fun request(method: String, endpoint: String, body: String? = null): SearchResponse

    fun existsIndex(index: String): Boolean = request("HEAD", "/$index").isOk

    fun deleteIndex(index: String): Boolean = request("DELETE", "/$index").isOk

    fun deleteIndexIfExists(index: String): Boolean =
        if (existsIndex(index)) deleteIndex(index) else false

    fun createIndex(index: String, body: String): Boolean = request("PUT", "/$index", body).isOk

    fun updateIndex(index: String, body: String): Boolean = request("PUT", "/$index/_mapping", body).isOk
}
