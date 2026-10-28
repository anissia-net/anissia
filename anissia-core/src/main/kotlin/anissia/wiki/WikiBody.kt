package anissia.wiki

import anissia.support.FailException
import anissia.support.Json
import tools.jackson.databind.JsonNode
import java.net.URI

data class WikiBody(val schemaVersion: Int = 1, val blocks: JsonNode = Json.readTree("[]")) {
    companion object {
        fun empty() = WikiBody(blocks = Json.readTree("[{\"w\":\"p\",\"ch\":[]}]"))
    }
}

data class WikiReferences(
    val templates: Set<Long>,
    val images: Set<String>,
    val templateUses: Map<Long, Int> = emptyMap(),
    val hasContent: Boolean = false,
)

object WikiBodyValidator {
    private const val maxBodyBytes = 1_048_576
    private val reservedAttrs = setOf("__proto__", "constructor", "prototype")
    private val wings = setOf("p", "br", "tf", "fs", "tc", "hl", "b", "i", "u", "s", "sup", "sub", "a", "fn",
        "ul", "li", "ol", "oli", "tl", "tli", "quote", "float", "details", "summary", "code", "hr", "table", "tr", "td", "img", "youtube", "exTemplateInclude")
    private val emptyWings = setOf("br", "hr", "img", "youtube", "fn", "exTemplateInclude")

    fun imageUrls(body: WikiBody, templates: Map<Long, WikiBody> = emptyMap()): List<String> {
        val urls = linkedSetOf<String>()
        fun visit(node: JsonNode, includeTemplates: Boolean) {
            when (node.path("w").asString("")) {
                "img" -> node.path("a").path("src").asString("").takeIf { it.isNotBlank() }?.let(urls::add)
                "exTemplateInclude" -> if (includeTemplates) {
                    node.path("a").path("exTemplateNo").asString("").toLongOrNull()?.let(templates::get)
                        ?.blocks?.forEach { visit(it, false) }
                }
            }
            node.path("ch").takeIf { it.isArray }?.forEach { visit(it, includeTemplates) }
        }
        body.blocks.forEach { visit(it, true) }
        return urls.toList()
    }

    fun analyze(body: WikiBody, template: Boolean = false): WikiReferences {
        fun invalid(): Nothing = throw FailException("올바른 위키 본문이 아닙니다.")
        if (body.schemaVersion != 1 || !body.blocks.isArray || body.blocks.isEmpty) invalid()
        val templates = linkedSetOf<Long>()
        val images = linkedSetOf<String>()
        val templateUses = linkedMapOf<Long, Int>()
        var hasContent = false
        var nodes = 0
        var characters = 0L
        fun countCharacters(count: Int) {
            characters += count
            if (characters > maxBodyBytes) invalid()
        }
        fun visit(node: JsonNode, depth: Int) {
            if (++nodes > 20_000 || depth > 40) invalid()
            if (node.isString) {
                countCharacters(node.asString().length)
                if (node.asString().any { it.isISOControl() && it !in "\n\r\t" }) invalid()
                if (node.asString().isNotBlank()) hasContent = true
                return
            }
            if (!node.isObject || node.propertyNames().any { it !in setOf("w", "a", "ch") }) invalid()
            val wing = node.path("w").asString("")
            if (wing !in wings) invalid()
            countCharacters(wing.length)
            if (wing in setOf("img", "exTemplateInclude", "youtube", "table", "hr", "fn")) hasContent = true
            val children = node.get("ch")
            if (children != null && !children.isArray) invalid()
            if (wing in emptyWings && children != null && !children.isEmpty) invalid()
            val attrs = node.get("a")
            if (attrs != null) {
                if (!attrs.isObject || attrs.size() > 32) invalid()
                for ((key, value) in attrs.properties()) {
                    if (key.length > 64 || key.startsWith('_') || key in reservedAttrs ||
                        key.startsWith("on", true) || key.equals("style", true) ||
                        !(value.isString || value.isNumber)) invalid()
                    countCharacters(key.length + value.asString("").length)
                    if (value.isNumber && !value.asDouble(Double.NaN).isFinite()) invalid()
                    if (value.isString) {
                        val text = value.asString()
                        if (text.length > 4096 || text.any(Char::isISOControl)) invalid()
                        if (key.lowercase() in setOf("src", "href") && !validUrl(text)) invalid()
                    }
                }
            }
            when (wing) {
                "exTemplateInclude" -> {
                    if (template) throw FailException("템플릿 안에 다른 템플릿을 넣을 수 없습니다.")
                    val id = attrs?.path("exTemplateNo")?.asString("")?.toLongOrNull()
                    if (id == null || id <= 0) invalid()
                    templates.add(id)
                    templateUses[id] = (templateUses[id] ?: 0) + 1
                }
                "img" -> {
                    val url = attrs?.path("src")?.asString("") ?: ""
                    if (!validUrl(url)) invalid()
                    images.add(url)
                }
                "a" -> if (!validUrl(attrs?.path("href")?.asString("") ?: "")) invalid()
                "fn" -> if (attrs?.path("t")?.asString("")?.isBlank() != false) invalid()
                "youtube" -> if (!Regex("[A-Za-z0-9_-]{11}").matches(attrs?.path("v")?.asString("") ?: "")) invalid()
            }
            children?.forEach { visit(it, depth + 1) }
        }
        body.blocks.forEach { visit(it, 0) }
        if (Json.write(body).toByteArray(Charsets.UTF_8).size > maxBodyBytes) invalid()
        if (templateUses.values.sum() > 50 || images.size > 100) invalid()
        return WikiReferences(templates, images, templateUses, hasContent)
    }

    fun validUrl(value: String): Boolean {
        if (value.isBlank() || value != value.trim() || value.length > 4096 || value.any(Char::isISOControl) || '\\' in value) return false
        if (value.startsWith("/") && !value.startsWith("//") && ':' !in value) return true
        if (value.startsWith("#")) return true
        return runCatching { URI(value).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
    }
}
