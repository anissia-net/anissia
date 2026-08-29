package anissia.support

open class FailException(
    message: String = DEFAULT_MESSAGE,
) : RuntimeException(message, null, false, false) {
    companion object {
        const val DEFAULT_MESSAGE = "알수없는 오류입니다."
    }
}

class BadRequestException(
    message: String,
) : FailException(message)

class ErrorException(
    message: String,
) : RuntimeException(message)
