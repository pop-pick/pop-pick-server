package com.poppick.poppick.feature.popup.dataaccess.client.openai

/** POST /v1/embeddings 요청. */
data class OpenAiEmbeddingRequest(
    val model: String,
    val input: List<String>,
    val dimensions: Int,
)
