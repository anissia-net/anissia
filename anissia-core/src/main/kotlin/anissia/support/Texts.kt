package anissia.support

import org.springframework.web.util.HtmlUtils
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Base64

object Texts {
    const val NAME_PATTERN = "[0-9A-Za-z가-힣㐀-䶵一-龻ぁ-ゖゝ-ヿ々_]{2,16}"
    const val MAIL_PATTERN = "[_a-z0-9\\-]+(\\.[_a-z0-9\\-]+)*@([_a-z0-9\\-]+\\.)+[a-z]{2,}"

    val NAME_REGEX: Regex = Regex(NAME_PATTERN)
    val MAIL_REGEX: Regex = Regex(MAIL_PATTERN)

    private val ANIME_DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
    private val BASE64_URL_ENCODER: Base64.Encoder = Base64.getUrlEncoder()
    private val BASE64_URL_DECODER: Base64.Decoder = Base64.getUrlDecoder()

    fun isName(value: String): Boolean = NAME_REGEX.matches(value)

    fun isMail(value: String): Boolean = MAIL_REGEX.matches(value)

    fun isWebSite(website: String, allowEmpty: Boolean = false): Boolean =
        (allowEmpty && website == "") || website.startsWith("https://") || website.startsWith("http://")

    fun isAnimeDate(animeDate: String): Boolean {
        if (animeDate.isEmpty()) {
            return true
        }
        if (!ANIME_DATE_REGEX.matches(animeDate)) {
            return false
        }
        return try {
            LocalDate.parse(animeDate.replace("-99", "-01"), DateFormats.ISO_YMD)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun encodeBase64Url(value: String): String =
        BASE64_URL_ENCODER.encodeToString(value.toByteArray(Charsets.UTF_8))

    fun decodeBase64Url(value: String): String =
        BASE64_URL_DECODER.decode(value).toString(Charsets.UTF_8)

    fun <T : Comparable<T>> sameElements(a: List<T>, b: List<T>): Boolean =
        a.size == b.size && a.sorted() == b.sorted()
}

fun String.escapeHtml(): String = HtmlUtils.htmlEscape(this)

fun String.encodeUrl(): String = URLEncoder.encode(this, Charsets.UTF_8)

val String.tokenNumber: Long get() = substringBefore('-', "0").toLong()

val String.tokenValue: String get() = substringAfter('-', "")
