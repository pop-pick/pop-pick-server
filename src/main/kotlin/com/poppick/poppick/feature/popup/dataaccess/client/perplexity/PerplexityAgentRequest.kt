package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

import com.fasterxml.jackson.annotation.JsonInclude

/** POST /v1/responses 요청. snake_case 로 직렬화된다. */
data class PerplexityAgentRequest(
    val model: String,
    val input: String,
    val instructions: String,
    val tools: List<Tool>,
    val responseFormat: ResponseFormat,
    val maxOutputTokens: Int = 1500,
) {
    /** 도구 1개. 실제 타입의 프로퍼티로 직렬화된다(타입 정보 필드는 따로 붙지 않는다). */
    sealed interface Tool {
        val type: String
    }

    data class WebSearchTool(
        val filters: Filters,
        override val type: String = "web_search",
        val searchContextSize: String = "medium",
        /** 검색 1회당 결과 수. 카카오 등록 팝업은 오래된 건도 많아 넉넉히 받는다. */
        val maxResults: Int = 20,
        val userLocation: UserLocation = UserLocation(),
    ) : Tool

    /** 이미지 검색. 결과는 output 의 image_search_results 항목으로 온다. 호출 1,000회당 $2.50. */
    data class ImageSearchTool(
        /** 호출 1회당 결과 수(1~30). */
        val maxResults: Int,
        override val type: String = "image_search",
    ) : Tool

    /** 값이 NULL 인 필터는 키 자체를 보내지 않는다(SearchRecency.NONE). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Filters(
        /** year 등. NULL 이면 최신성 필터 없음. */
        val searchRecencyFilter: String?,
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
