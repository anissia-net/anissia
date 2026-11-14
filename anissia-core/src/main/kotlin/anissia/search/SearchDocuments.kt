package anissia.search

import anissia.support.Texts
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

internal fun animeStartDateSort(startDate: String): Long? {
    if (startDate.isEmpty() || !Texts.isAnimeDate(startDate)) return null
    return startDate.replace("-99", "-00").replace("-", "").toLong()
}

@Service
class SearchDocuments(private val jdbc: JdbcTemplate) {
    fun ids(kind: SearchKind, after: String, limit: Int = 100): List<String> = jdbc.query(
        "SELECT ${kind.idColumn} FROM ${kind.table} WHERE ${kind.idColumn} > ? ORDER BY ${kind.idColumn} LIMIT ?",
        { row, _ -> row.getString(1) }, after, limit,
    )

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun load(kind: SearchKind, id: String): Map<String, Any>? = loadBatch(kind, listOf(id))[id]

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun loadBatch(kind: SearchKind, ids: List<String>): Map<String, Map<String, Any>> {
        if (ids.isEmpty()) return emptyMap()
        val rows = select("SELECT * FROM ${kind.table} WHERE ${kind.idColumn} IN (:ids)", ids)
        val translators = if (kind == SearchKind.ANIME) select("SELECT c.anime_no, a.name FROM anime_caption c JOIN account a ON a.an = c.an WHERE c.anime_no IN (:ids)", ids)
            .groupBy { it.text("anime_no") }.mapValues { (_, captions) -> captions.map { it.text("name") } } else emptyMap()
        val wikis = if (kind == SearchKind.ANIME) select("SELECT anime_no, body FROM anime_wiki WHERE anime_no IN (:ids)", ids)
            .associate { it.text("anime_no") to it.text("body") } else emptyMap()
        val posts = if (kind == SearchKind.BOARD) select("SELECT topic_no, root, content FROM board_post WHERE topic_no IN (:ids) ORDER BY post_no", ids)
            .groupBy { it.text("topic_no") } else emptyMap()
        return rows.mapNotNull { row ->
            val id = row.text(kind.idColumn)
            document(kind, id, row, translators[id].orEmpty(), wikis[id].orEmpty(), posts[id].orEmpty())?.let { id to it }
        }.toMap()
    }

    private fun select(sql: String, ids: Collection<*>): List<Map<String, Any?>> = if (ids.isEmpty()) emptyList()
        else jdbc.queryForList(sql.replace(":ids", ids.joinToString(",") { "?" }), *ids.toTypedArray())

    private fun document(kind: SearchKind, id: String, row: Map<String, Any?>, translators: List<String>, wiki: String,
        posts: List<Map<String, Any?>>): Map<String, Any>? {
        val doc = mutableMapOf<String, Any>("id" to id)
        when (kind) {
            SearchKind.ANIME -> {
                if (row["deleted_at"] != null) return null
                doc["title"] = row.text("subject")
                doc["originalTitle"] = row.text("original_subject")
                doc["status"] = row.text("status")
                doc["genres"] = row.text("genres").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val startDate = row.text("start_date")
                animeStartDateSort(startDate)?.let { doc["startDateSort"] = it }
                doc["endDate"] = row.text("end_date").replace("-", "").toLongOrNull() ?: 0L
                doc["translators"] = translators
                val week = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "기타", "신작").getOrNull(row.text("week").toIntOrNull() ?: -1).orEmpty()
                doc["info"] = listOf(week, row.text("time"), row.text("genres"), row.text("note"), startDate, row.text("end_date"), row.text("website"), row.text("x"),
                    when (row.text("status")) { "END" -> "완결"; "ON" -> if (row.text("week") in listOf("7", "8")) "신작 기타" else "방영중"; else -> "결방" },
                    (doc["translators"] as List<*>).joinToString(" ")).joinToString(" ")
                doc["body"] = SearchText.wiki(wiki)
            }
            SearchKind.BOARD -> {
                doc["title"] = row.text("topic")
                doc["ticker"] = row.text("ticker")
                fun root(post: Map<String, Any?>) = post["root"].let { it == true || it is Number && it.toInt() != 0 }
                doc["body"] = posts.filter(::root).joinToString("\n") { SearchText.html(it.text("content")) }
                doc["comments"] = posts.filterNot(::root).joinToString("\n") { SearchText.html(it.text("content")) }
            }
            SearchKind.TEMPLATE -> {
                doc["title"] = row.text("title")
                doc["body"] = SearchText.wiki(row.text("body"))
                doc["status"] = if (row["deleted"].let { it == true || it is Number && it.toInt() != 0 }) "DELETED" else "READY"
            }
            SearchKind.IMAGE -> {
                if (row.text("status") !in setOf("READY", "DELETING", "DELETED", "RESTORING")) return null
                doc["title"] = row.text("description")
                doc["originalTitle"] = id
                doc["body"] = row.text("source_url")
                doc["status"] = row.text("status")
            }
        }
        return doc
    }

    private fun Map<String, Any?>.text(key: String) = get(key)?.toString().orEmpty()
}
