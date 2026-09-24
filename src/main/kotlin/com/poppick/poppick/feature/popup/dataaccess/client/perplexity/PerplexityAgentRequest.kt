package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

/** POST /v1/responses 요청. snake_case 로 직렬화된다. */
data class PerplexityAgentRequest(
    val model: String,
    val input: String,
    val instructions: String,
    val tools: List<WebSearchTool>,
    val responseFormat: ResponseFormat,
    val maxOutputTokens: Int = 1500,
) {
    data class WebSearchTool(
        val filters: Filters,
        val type: String = "web_search",
        val searchContextSize: String = "medium",
        val maxResults: Int = 10,
        val userLocation: UserLocation = UserLocation(),
    )

    data class Filters(
        /** month · year 등. */
        val searchRecencyFilter: String,
        /** "-도메인" 은 제외. */
        val searchDomainFilter: List<String> = listOf("-popupkorea.co.kr"),
    )

    data class UserLocation(
        val country: String = "KR",
        val city: String = "Seoul",
    )

    data class ResponseFormat(
        val jsonSchema: JsonSchema,
        val type: String = "json_schema",
    )

    data class JsonSchema(
        val name: String,
        /** 리소스 파일에서 읽은 JSON Schema 원문. Map 키는 naming strategy 영향을 받지 않는다. */
        val schema: Map<String, Any?>,
    )
}
