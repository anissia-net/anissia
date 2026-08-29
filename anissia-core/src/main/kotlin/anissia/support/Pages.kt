package anissia.support

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl

fun <T : Any, U : Any> Page<U>.replaceContent(content: List<T>): Page<T> =
    PageImpl(content, pageable, totalElements)

fun <T : Any> Page<T>.filterContent(predicate: (T) -> Boolean): Page<T> =
    PageImpl(content.filter(predicate), pageable, totalElements)
