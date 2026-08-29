package anissia.activepanel.api.dto

import anissia.activepanel.domain.ActivePanel
import anissia.support.badRequestUnless
import com.fasterxml.jackson.annotation.JsonInclude

class ActivePanelItem(
    val apNo: Long,
    val published: Boolean,
    val code: String,
    val status: String,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val data1: String?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val data2: String?,
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val data3: String?,
    val regTime: Long,
) {
    constructor(activePanel: ActivePanel) : this(
        apNo = activePanel.apNo,
        published = activePanel.published,
        code = activePanel.code,
        status = activePanel.status,
        data1 = activePanel.data1,
        data2 = activePanel.data2,
        data3 = activePanel.data3,
        regTime = activePanel.regDt.toEpochSecond(),
    )
}

data class ActivePanelCommandRequest(
    val query: String = "",
) {
    val published: Boolean get() = query.startsWith("!")
    val isCommand: Boolean get() = query.startsWith("/")

    val text: String
        get() = when {
            published -> query.substring(1)
            isCommand -> query.substring(1)
            else -> query
        }

    fun validate() {
        badRequestUnless(query.isNotBlank()) { "내용을 입력해주세요." }
    }
}
