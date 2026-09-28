package com.poppick.poppick.feature.popup.dataaccess.client.openai

/** POST /v1/responses 응답 중 필요한 부분만. */
data class OpenAiChatResponse(
    /** completed · incomplete 등. incomplete 면 incompleteDetails.reason 에 사유(max_output_tokens 등). */
    val status: String? = null,
    val incompleteDetails: IncompleteDetails? = null,
    val output: List<OutputItem> = emptyList(),
    val usage: Usage? = null,
) {
    data class IncompleteDetails(
        val reason: String? = null,
    )

    /** type = message 만 쓴다(content 의 output_text). refusal 등 다른 content 는 무시. */
    data class OutputItem(
        val type: String,
        val content: List<Content>? = null,
    )

    data class Content(
        val type: String,
        val text: String? = null,
    )

    data class Usage(
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
    )

    companion object {
        const val STATUS_COMPLETED = "completed"
        const val TYPE_MESSAGE = "message"
        const val TYPE_OUTPUT_TEXT = "output_text"
    }

    fun outputText() =
        output
            .filter { it.type == TYPE_MESSAGE }
            .flatMap { it.content.orEmpty() }
            .filter { it.type == TYPE_OUTPUT_TEXT }
            .joinToString("") { it.text.orEmpty() }
}
