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
    fun load(kind: SearchKind, id: String): Map<String, Any>? = loadBatch(kind, listOf(id))[id]

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    fun loadBatch(kind: SearchKind, ids: List<String>): Map<String, Map<String, Any>> {
        if (ids.isEmpty()) return emptyMap()
        val rows = select("SELECT * FROM ${kind.table} WHERE ${kind.idColumn} IN (:ids)", ids)
        val translators = if (kind == SearchKind.ANIME) select("SELECT c.anime_no, a.name FROM anime_caption c JOIN account a ON a.an = c.an WHERE c.anime_no IN (:ids)", ids)
            .groupBy { it.text("anime_no") }.mapValues { (_, captions) -> captions.map { it.text("name") } } else emptyMap()
        val wikis = if (kind == SearchKind.ANIME) select("SELECT anime_no, body FROM anime_wiki WHERE anime_no IN (:ids)", ids)
            .associate { it.text("anime_no") to it.text("body") } else emptyMap()
        val references = wikis.filterValues { it.isNotBlank() }.mapValues { (_, body) -> WikiBodyValidator.analyze(Json.read<WikiBody>(body)) }
        val templateIds = references.values.flatMap { it.templates }.distinct()
        val templates = select("SELECT template_no, title, body, revision FROM wiki_template WHERE template_no IN (:ids) AND deleted = false", templateIds)
            .associate { template ->
                val no = (template["template_no"] as Number).toLong()
                val snapshot = WikiTemplateSnapshot(no, template.text("title"), Json.read(template.text("body")), (template["revision"] as Number).toLong())
                no to (template.text("body") to Json.write(snapshot).toByteArray().size.toLong())
            }
        val posts = if (kind == SearchKind.BOARD) select("SELECT topic_no, root, content FROM board_post WHERE topic_no IN (:ids) ORDER BY post_no", ids)
            .groupBy { it.text("topic_no") } else emptyMap()
        return rows.mapNotNull { row ->
            val id = row.text(kind.idColumn)
            val wiki = wikis[id].orEmpty()
            val included = linkedMapOf<Long, String>()
            var bytes = wiki.toByteArray().size.toLong()
            references[id]?.let { refs ->
                refs.templates.sorted().forEach { no -> templates[no]?.let { (body, size) ->
                    val length = size * refs.templateUses.getValue(no)
                    if (bytes + length <= 4L * 1024 * 1024) { included[no] = body; bytes += length }
                } }
            }
            document(kind, id, row, translators[id].orEmpty(), wiki, included, posts[id].orEmpty())?.let { id to it }
        }.toMap()
    }

    private fun select(sql: String, ids: Collection<*>): List<Map<String, Any?>> = if (ids.isEmpty()) emptyList()
        else jdbc.queryForList(sql.replace(":ids", ids.joinToString(",") { "?" }), *ids.toTypedArray())

    private fun document(kind: SearchKind, id: String, row: Map<String, Any?>, translators: List<String>, wiki: String,
        included: Map<Long, String>, posts: List<Map<String, Any?>>): Map<String, Any>? {
        val doc = mutableMapOf<String, Any>("id" to id)
        when (kind) {
            SearchKind.ANIME -> {
                doc["title"] = row.text("subject")
                doc["originalTitle"] = row.text("original_subject")
                doc["status"] = row.text("status")
                doc["genres"] = row.text("genres").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                doc["endDate"] = row.text("end_date").replace("-", "").toLongOrNull() ?: 0L
                doc["translators"] = translators
                val week = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "기타", "신작").getOrNull(row.text("week").toIntOrNull() ?: -1).orEmpty()
                doc["info"] = listOf(week, row.text("time"), row.text("genres"), row.text("note"), row.text("start_date"), row.text("end_date"), row.text("website"), row.text("x"),
                    when (row.text("status")) { "END" -> "완결"; "ON" -> if (row.text("week") in listOf("7", "8")) "신작 기타" else "방영중"; else -> "결방" },
                    (doc["translators"] as List<*>).joinToString(" ")).joinToString(" ")
                doc["body"] = SearchText.wiki(wiki, included)
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
