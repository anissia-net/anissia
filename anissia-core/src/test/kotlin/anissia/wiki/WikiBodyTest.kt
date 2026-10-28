package anissia.wiki

import anissia.support.FailException
import anissia.support.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.JsonNode

class WikiBodyTest {
    private fun body(blocks: String) = WikiBody(blocks = Json.readTree(blocks))

    @Test
    fun `registered editor nodes collect stable template references and ready image URLs`() {
        val refs = WikiBodyValidator.analyze(body("""[
            {"w":"p","ch":["본문",{"w":"b","ch":["강조"]},{"w":"fn","a":{"t":"출처"},"ch":[]}]},
            {"w":"img","a":{"src":"https://file.anissia.net/wiki/abc.avif"},"ch":[]},
            {"w":"exTemplateInclude","a":{"exTemplateNo":"12"},"ch":[]},
            {"w":"exTemplateInclude","a":{"exTemplateNo":"12"},"ch":[]}
        ]"""))
        assertEquals(setOf(12L), refs.templates)
        assertEquals(setOf("https://file.anissia.net/wiki/abc.avif"), refs.images)
        assertEquals(mapOf(12L to 2), refs.templateUses)
    }

    @Test
    fun `template nesting is rejected at any depth`() {
        val nested = body("""[{"w":"table","ch":[{"w":"tr","ch":[{"w":"td","ch":[
            {"w":"exTemplateInclude","a":{"exTemplateNo":"3"},"ch":[]}
        ]}]}]}]""")
        assertThrows<FailException> { WikiBodyValidator.analyze(nested, template = true) }
    }

    @Test
    fun `malformed nodes unsafe attributes and active URLs are rejected`() {
        val invalid = listOf(
            "[]", "{}", "[null]", "[1]", "[{\"w\":\"script\",\"ch\":[]}]",
            "[{\"w\":\"p\",\"ch\":{}}]", "[{\"w\":\"p\",\"html\":\"x\"}]",
            "[{\"w\":\"p\",\"a\":{\"onclick\":\"alert(1)\"}}]",
            "[{\"w\":\"p\",\"a\":{\"style\":\"display:none\"}}]",
            "[{\"w\":\"a\",\"a\":{\"href\":\"javascript:alert(1)\"},\"ch\":[\"링크\"]}]",
            "[{\"w\":\"img\",\"a\":{\"src\":\"data:image/svg+xml,<svg/>\"},\"ch\":[]}]",
            "[{\"w\":\"fn\",\"a\":{\"t\":\" \"},\"ch\":[]}]",
            "[{\"w\":\"img\",\"a\":{\"src\":\"https://example.org/a.png\"},\"ch\":[\"x\"]}]",
            "[{\"w\":\"exTemplateInclude\",\"a\":{\"templateNo\":\"12\"},\"ch\":[]}]",
        )
        invalid.forEach { value -> assertThrows<FailException>(value) { WikiBodyValidator.analyze(body(value)) } }
        assertThrows<FailException> { WikiBodyValidator.analyze(WikiBody(schemaVersion = 2, blocks = WikiBody.empty().blocks)) }
    }

    @Test
    fun `depth node and byte budgets reject oversized input`() {
        var nested = "\"text\""
        repeat(42) { nested = "{\"w\":\"p\",\"ch\":[$nested]}" }
        assertThrows<FailException> { WikiBodyValidator.analyze(body("[$nested]")) }
        assertThrows<FailException> { WikiBodyValidator.analyze(body("[${List(20_001) { "\"x\"" }.joinToString(",")}]")) }
        assertThrows<FailException> { WikiBodyValidator.analyze(body("[\"${"가".repeat(360_000)}\"]")) }
    }

    @Test
    fun `links reject protocol relative credentials and obfuscated controls`() {
        listOf("javascript:alert(1)", "data:text/html,x", "//evil.example/a", "https://u:p@example.org/", " https://example.org/", "https://example.org/\n", "/\\evil.example/a").forEach {
            assertFalse(WikiBodyValidator.validUrl(it), it)
        }
        listOf("https://example.org/a?q=1#top", "/anime?animeNo=1", "#reference").forEach {
            assertTrue(WikiBodyValidator.validUrl(it), it)
        }
    }

    @Test
    fun `attribute keys rejected by the viewer cannot be persisted`() {
        for (key in listOf("__proto__", "constructor", "prototype", "_id", "_internal")) {
            val value = body("""[{"w":"p","a":{"$key":"bad"},"ch":["본문"]}]""")
            assertThrows<FailException>(key) { WikiBodyValidator.analyze(value) }
        }
    }

    @Test
    fun `deep input is rejected before recursive JSON serialization`() {
        var nested: JsonNode = Json.readTree("\"본문\"")
        repeat(1200) {
            nested = Json.objectNode().put("w", "p").apply { putArray("ch").add(nested) }
        }
        val value = WikiBody(blocks = Json.mapper.createArrayNode().add(nested))
        assertThrows<FailException> { WikiBodyValidator.analyze(value) }
    }

    @Test
    fun `numeric attributes must be finite`() {
        val attrs = Json.objectNode().put("w", Double.POSITIVE_INFINITY)
        val node = Json.objectNode().put("w", "p").apply {
            set("a", attrs)
            putArray("ch").add("본문")
        }
        assertThrows<FailException> { WikiBodyValidator.analyze(WikiBody(blocks = Json.mapper.createArrayNode().add(node))) }
    }
}
