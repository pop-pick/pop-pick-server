package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** OpenAI Embeddings · Responses API 설정. 값은 전부 yml 의 openai.* 에서 받는다. */
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
    /** 플래너 코스 선정(Responses API) 모델. */
    val chatModel: String,
    /** Responses API 읽기 타임아웃. 생성 응답이라 임베딩보다 길게 둔다. */
    val chatReadTimeoutSeconds: Long,
    /** Responses API 출력 토큰 상한(max_output_tokens). 넘으면 incomplete 로 끊겨 실패 처리된다. */
    val chatMaxOutputTokens: Int = 1024,
)
