package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

/** POST /v1/responses 응답 중 필요한 부분만. */
data class PerplexityAgentResponse(
    val output: List<OutputItem> = emptyList(),
    val usage: Usage? = null,
) {
    /** type = message(content 사용) 또는 search_results(results 사용). 그 외 타입은 무시. */
    data class OutputItem(
        val type: String,
        val content: List<Content>? = null,
        val results: List<SearchResult>? = null,
    )

    data class Content(
        val type: String,
        val text: String? = null,
    )

    data class SearchResult(
        val url: String? = null,
        val title: String? = null,
    )

    data class Usage(
        val cost: Cost? = null,
    )

    data class Cost(
        val totalCost: Double? = null,
    )

    companion object {
        const val TYPE_MESSAGE = "message"
        const val TYPE_OUTPUT_TEXT = "output_text"
        const val TYPE_SEARCH_RESULTS = "search_results"
    }

    fun outputText() =
        output
            .filter { it.type == TYPE_MESSAGE }
            .flatMap { it.content.orEmpty() }
            .filter { it.type == TYPE_OUTPUT_TEXT }
            .joinToString("") { it.text.orEmpty() }

    /** 검색을 여러 번 하면 search_results 항목이 여러 개 온다. 전부 모아 등장 순으로 중복 제거. */
    fun searchResultUrls() =
        output
            .filter { it.type == TYPE_SEARCH_RESULTS }
            .flatMap { it.results.orEmpty() }
            .mapNotNull { it.url?.takeIf { url -> url.isNotBlank() } }
            .distinct()
}
