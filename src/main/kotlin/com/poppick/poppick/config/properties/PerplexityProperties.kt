package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** Perplexity Agent API 설정. 값은 전부 yml 의 perplexity.* 에서 받는다. */
@ConfigurationProperties("perplexity")
data class PerplexityProperties(
    val apiKey: String,
    val baseUrl: String,
    /** json_schema + web_search 조합이 동작하는 모델만 쓴다(anthropic 계열 금지). */
    val model: String,
    val readTimeoutSeconds: Long,
    /** 보강 요청 속도 제한(초당 요청 수). */
    val requestsPerSecond: Double,
)
