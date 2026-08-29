package anissia.support

fun badRequest(message: String): Nothing = throw BadRequestException(message)

inline fun badRequestIf(condition: Boolean, message: () -> String) {
    if (condition) badRequest(message())
}

inline fun badRequestUnless(condition: Boolean, message: () -> String) {
    if (!condition) badRequest(message())
}

inline fun <T> badRequestOnFailure(message: String, block: () -> T): T =
    try {
        block()
    } catch (_: Exception) {
        badRequest(message)
    }

fun fail(message: String): Nothing = throw FailException(message)

inline fun failIf(condition: Boolean, message: () -> String) {
    if (condition) fail(message())
}
