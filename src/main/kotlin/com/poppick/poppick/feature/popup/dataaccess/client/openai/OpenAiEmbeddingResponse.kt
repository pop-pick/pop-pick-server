package com.poppick.poppick.feature.popup.dataaccess.client.openai

/** POST /v1/embeddings 응답 중 필요한 부분만. */
data class OpenAiEmbeddingResponse(
    val data: List<Item> = emptyList(),
    val usage: Usage? = null,
) {
    data class Item(
        /** 요청 input 에서의 위치. 응답 순서는 보장되지 않아 이 값으로 정렬한다. */
        val index: Int,
        val embedding: FloatArray,
    )

    data class Usage(
        val promptTokens: Int? = null,
    )
}
