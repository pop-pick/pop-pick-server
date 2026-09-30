package com.poppick.poppick.feature.popup.dataaccess.client.openai

import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode

/** POST /v1/responses 요청(Structured Outputs). snake_case 로 직렬화되고 null 필드는 생략된다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenAiChatRequest(
    val model: String,
    val instructions: String,
    val input: String,
    val text: Text,
    val maxOutputTokens: Int,
    /** gpt-5 · o 계열은 temperature 를 받지 않으므로 null 이면 보내지 않는다. */
    val temperature: Double? = null,
    val reasoning: Reasoning? = null,
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

    /** reasoning 모델(gpt-5 · o 계열) 전용. effort = minimal · low · medium · high 등. */
    data class Reasoning(
        val effort: String,
    )
}
