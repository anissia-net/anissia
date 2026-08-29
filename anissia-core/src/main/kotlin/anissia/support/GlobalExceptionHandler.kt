package anissia.support

import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.NestedRuntimeException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestValueException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = logger<GlobalExceptionHandler>()

    @ExceptionHandler(Error::class)
    fun handleError(ex: Error, request: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(UNKNOWN_ERROR_MESSAGE))
            .also { logError(ex, request) }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse<Unit>> =
        when (ex) {
            is HttpMessageNotReadableException,
            is MethodArgumentTypeMismatchException,
            is MissingRequestValueException,
            is HandlerMethodValidationException,
                -> ok(ApiResponse.error(INVALID_INPUT_MESSAGE))

            is IllegalArgumentException, is FailException ->
                ok(ApiResponse.error(ex.message ?: ex.javaClass.simpleName))

            is HttpRequestMethodNotSupportedException ->
                ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResponse.error("METHOD_NOT_ALLOWED"))
                    .also { log.info("MNA ${request.method} ${request.requestURI} ${request.clientIp}") }

            is ResponseStatusException ->
                ResponseEntity.status(ex.statusCode.value()).body(ApiResponse.error(ex.message))
                    .also { log.info("RSE ${request.method} ${request.requestURI} ${ex.message} ${request.clientIp}") }

            is MethodArgumentNotValidException, is BindException ->
                ok(ApiResponse.error(firstBindErrorMessage(ex)))

            is SecurityException ->
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.message))
                    .also { logError(ex, request) }

            is ErrorResponse ->
                ResponseEntity.status(ex.statusCode.value()).body(ApiResponse.error(ex.message))
                    .also {
                        log.info(
                            "ERS ${request.method} ${request.requestURI} ${ex.message} ${request.clientIp}",
                        )
                    }

            is NestedRuntimeException, is ErrorException ->
                ok(ApiResponse.error(ex.message ?: ex.javaClass.simpleName)).also { logError(ex, request) }

            else -> ok(ApiResponse.error("unknown error")).also { logError(ex, request) }
        }

    private fun ok(body: ApiResponse<Unit>): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(HttpStatus.OK).body(body)

    private fun firstBindErrorMessage(ex: Exception): String =
        try {
            (ex as BindException).bindingResult.allErrors[0].defaultMessage ?: INVALID_INPUT_MESSAGE
        } catch (_: Exception) {
            INVALID_INPUT_MESSAGE
        }

    private fun logError(throwable: Throwable, request: HttpServletRequest) {
        log.error("${throwable.message}\n${request.method} ${request.requestURI}", throwable)
    }

    companion object {
        private const val UNKNOWN_ERROR_MESSAGE = "알수없는 오류 입니다."
        private const val INVALID_INPUT_MESSAGE = "입력값이 잘못되었습니다."
    }
}

val HttpServletRequest.clientIp: String get() = remoteAddr ?: "0.0.0.0"
