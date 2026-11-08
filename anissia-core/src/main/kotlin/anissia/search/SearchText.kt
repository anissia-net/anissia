package anissia.search

import anissia.support.Json
import anissia.wiki.WikiBody
import tools.jackson.databind.JsonNode
import java.io.StringReader
import javax.swing.text.MutableAttributeSet
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.parser.ParserDelegator

object SearchText {
    fun wiki(json: String): String {
        if (json.isBlank()) return ""
        val body = Json.read<WikiBody>(json)
        val text = StringBuilder()
        fun visit(node: JsonNode) {
            if (node.isString) { text.append(node.asString()).append(' '); return }
            when (node.path("w").asString("")) {
                "fn" -> text.append(node.path("a").path("t").asString("")).append(' ')
                "img" -> text.append(node.path("a").path("alt").asString("")).append(' ')
                "exTemplateInclude" -> return
            }
            node.path("ch").takeIf { it.isArray }?.forEach(::visit)
        }
        body.blocks.forEach(::visit)
        return text.toString().trim()
    }

    fun html(html: String): String {
        val text = StringBuilder()
        var hidden = 0
        ParserDelegator().parse(StringReader(html), object : HTMLEditorKit.ParserCallback() {
            override fun handleStartTag(tag: HTML.Tag, attrs: MutableAttributeSet, pos: Int) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE || tag == HTML.Tag.HEAD) hidden++
            }
            override fun handleEndTag(tag: HTML.Tag, pos: Int) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE || tag == HTML.Tag.HEAD) hidden--
                text.append(' ')
            }
            override fun handleText(data: CharArray, pos: Int) { if (hidden == 0) text.append(data).append(' ') }
        }, true)
        return text.toString().trim()
    }
}
