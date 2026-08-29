package anissia.support

import com.fasterxml.jackson.annotation.JsonInclude

class ApiResponse<T> private constructor(
    val code: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val message: String? = null,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val data: T? = null,
) {
    companion object {
        const val OK = "ok"
        const val FAIL = "fail"
        const val ERROR = "error"

        fun ok(): ApiResponse<Unit> = ApiResponse(OK)

        fun <T> ok(data: T?): ApiResponse<T> = ApiResponse(OK, null, data)

        fun fail(message: String? = ""): ApiResponse<Unit> = ApiResponse(FAIL, message)

        fun <T> fail(message: String, data: T?): ApiResponse<T> = ApiResponse(FAIL, message, data)

        fun error(message: String?): ApiResponse<Unit> = ApiResponse(ERROR, message)

        fun <T> error(message: String, data: T?): ApiResponse<T> = ApiResponse(ERROR, message, data)

        fun <T> of(code: String, message: String? = null, data: T? = null): ApiResponse<T> =
            ApiResponse(code, message, data)
    }
}
