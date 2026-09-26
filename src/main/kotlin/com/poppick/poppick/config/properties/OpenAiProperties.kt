package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** OpenAI Embeddings API 설정. 값은 전부 yml 의 openai.* 에서 받는다. */
@ConfigurationProperties("openai")
data class OpenAiProperties(
    val apiKey: String,
    val baseUrl: String,
    val embeddingModel: String,
    /** 요청 · 응답 벡터 차원. popup_embedding.embedding 컬럼 차원(1536)과 같아야 한다. */
    val embeddingDimensions: Int,
    val readTimeoutSeconds: Long,
    /** 요청 1회에 넣는 입력 수. */
    val batchSize: Int,
)
