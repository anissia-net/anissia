package anissia.support

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateFormats {
    val KST: ZoneOffset = ZoneOffset.ofHours(9)
    val ISO_YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val ISO_YMDHMS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    val CAPTION: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val RANK_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH")
    val USER_YMD: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일")
    val USER_YMDHMS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH시 mm분 ss초")
}
