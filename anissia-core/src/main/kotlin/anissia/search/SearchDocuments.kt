package anissia.search

import anissia.support.Json
import anissia.wiki.WikiBody
import anissia.wiki.WikiBodyValidator
import anissia.wiki.WikiTemplateSnapshot
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SearchDocuments(private val jdbc: JdbcTemplate) {
    fun ids(kind: SearchKind, after: String, limit: Int = 100): List<String> = jdbc.query(
        "SELECT ${kind.idColumn} FROM ${kind.table} WHERE ${kind.idColumn} > ? ORDER BY ${kind.idColumn} LIMIT ?",
        { row, _ -> row.getString(1) }, after, limit,
    )

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun load(kind: SearchKind, id: String): Map<String, Any>? {
        val row = jdbc.queryForList("SELECT * FROM ${kind.table} WHERE ${kind.idColumn} = ?", id).firstOrNull() ?: return null
        val doc = mutableMapOf<String, Any>("id" to id)
        when (kind) {
            SearchKind.ANIME -> {
                doc["title"] = row.text("subject")
                doc["originalTitle"] = row.text("original_subject")
                doc["status"] = row.text("status")
                doc["genres"] = row.text("genres").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                doc["endDate"] = row.text("end_date").replace("-", "").toLongOrNull() ?: 0L
                doc["translators"] = jdbc.query("SELECT a.name FROM anime_caption c JOIN account a ON a.an = c.an WHERE c.anime_no = ?", { r, _ -> r.getString(1) }, id)
                val week = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "기타", "신작").getOrNull(row.text("week").toIntOrNull() ?: -1).orEmpty()
                doc["info"] = listOf(week, row.text("time"), row.text("genres"), row.text("note"), row.text("start_date"), row.text("end_date"), row.text("website"), row.text("x"),
                    when (row.text("status")) { "END" -> "완결"; "ON" -> if (row.text("week") in listOf("7", "8")) "신작 기타" else "방영중"; else -> "결방" },
                    (doc["translators"] as List<*>).joinToString(" ")).joinToString(" ")
                val wiki = jdbc.queryForList("SELECT body FROM anime_wiki WHERE anime_no = ?", id).firstOrNull()?.text("body").orEmpty()
                val included = linkedMapOf<Long, String>()
                if (wiki.isNotBlank()) {
                    val refs = WikiBodyValidator.analyze(Json.read<WikiBody>(wiki))
                    var bytes = wiki.toByteArray().size.toLong()
                    refs.templates.sorted().forEach { templateNo ->
                        jdbc.queryForList("SELECT template_no, title, body, revision FROM wiki_template WHERE template_no = ? AND deleted = false", templateNo).firstOrNull()?.let { template ->
                            val snapshot = WikiTemplateSnapshot(templateNo, template.text("title"), Json.read(template.text("body")), (template["revision"] as Number).toLong())
                            val length = Json.write(snapshot).toByteArray().size.toLong() * refs.templateUses.getValue(templateNo)
                            if (bytes + length <= 4L * 1024 * 1024) { included[templateNo] = template.text("body"); bytes += length }
                        }
                    }
                }
                doc["body"] = SearchText.wiki(wiki, included)
            }
            SearchKind.BOARD -> {
                doc["title"] = row.text("topic")
                doc["ticker"] = row.text("ticker")
                val posts = jdbc.queryForList("SELECT root, content FROM board_post WHERE topic_no = ? ORDER BY post_no", id)
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
