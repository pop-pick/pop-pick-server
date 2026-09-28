package com.poppick.poppick.feature.popup.dataaccess.client.openai

import tools.jackson.databind.JsonNode

/** POST /v1/responses 요청(Structured Outputs). snake_case 로 직렬화된다. */
data class OpenAiChatRequest(
    val model: String,
    val instructions: String,
    val input: String,
    val text: Text,
    val maxOutputTokens: Int,
    val temperature: Double = 0.7,
) {
    data class Text(
        val format: Format,
    )

    data class Format(
        val name: String,
        /** 리소스 파일에서 읽은 JSON Schema 원문. JsonNode 키는 naming strategy 영향을 받지 않는다. */
        val schema: JsonNode,
        val type: String = "json_schema",
        val strict: Boolean = true,
    )
}
