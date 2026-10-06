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
    /**
     * 보강 요청의 max_steps(에이전트 루프 횟수, 1 스텝 = 도구를 부를 수 있는 모델 턴 1회).
     * 생략하면 API 가 1 로 둬서 재검색이 불가능하다. 프롬프트의 검색 상한(6회)과 맞춘다.
     */
    val enrichMaxSteps: Int,
    /** 보강 요청의 max_output_tokens. 스텝이 늘면 1500 으로는 응답이 잘려(incomplete) 여유 있게 둔다. */
    val enrichMaxOutputTokens: Int,
)
