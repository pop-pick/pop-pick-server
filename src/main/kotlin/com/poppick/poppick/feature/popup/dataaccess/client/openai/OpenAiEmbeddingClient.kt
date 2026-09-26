package com.poppick.poppick.feature.popup.dataaccess.client.openai

import com.poppick.poppick.config.properties.OpenAiProperties
import com.poppick.poppick.feature.popup.dataaccess.client.snakeCase
import com.poppick.poppick.feature.popup.domain.EmbeddingResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.body
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }

@Component
class OpenAiEmbeddingClient(
    jsonMapper: JsonMapper,
    private val properties: OpenAiProperties,
) {
    companion object {
        private const val EMBEDDINGS_PATH = "/v1/embeddings"

        /** text-embedding-3-small 단가(USD / 1M 토큰). 출처: OpenAI 가격표, 2026-09-26 기준. */
        const val PRICE_PER_MILLION_TOKENS_USD = 0.02

        /** 429 · 5xx · 타임아웃 재시도 간격(Retry-After 가 있으면 그 값). 길이 = 최대 재시도 횟수. */
        private val BACKOFF = listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4))
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
    }

    /** 테스트에서 MockRestServiceServer 에 바인딩한 RestClient 로 교체한다. */
    internal var restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(properties.baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds)) },
            ).build()

    private val mapper = jsonMapper.snakeCase()

    /** 재시도 대기. 테스트에서 교체한다. */
    internal var sleeper: (Duration) -> Unit = { Thread.sleep(it) }

    fun embed(texts: List<String>): EmbeddingResult {
        val request = OpenAiEmbeddingRequest(properties.embeddingModel, texts, properties.embeddingDimensions)
        val response = parseResponse(postWithRetry(mapper.writeValueAsBytes(request)))

        val vectors = response.data.sortedBy { it.index }.map { it.embedding }
        if (vectors.size != texts.size) {
            throw OpenAiClientException("OpenAI 응답 벡터 수 불일치 expected=${texts.size} actual=${vectors.size}")
        }
        vectors.firstOrNull { it.size != properties.embeddingDimensions }?.let {
            throw OpenAiClientException("OpenAI 응답 벡터 차원 불일치 expected=${properties.embeddingDimensions} actual=${it.size}")
        }

        val promptTokens = response.usage?.promptTokens ?: 0
        return EmbeddingResult(
            vectors = vectors,
            promptTokens = promptTokens,
            costUsd = promptTokens * PRICE_PER_MILLION_TOKENS_USD / 1_000_000,
        )
    }

    private fun postWithRetry(body: ByteArray): ByteArray {
        var attempt = 0
        while (true) {
            val delay: Duration =
                try {
                    return post(body)
                } catch (e: RestClientResponseException) {
                    val status = e.statusCode
                    if (status.value() != HttpStatus.TOO_MANY_REQUESTS.value() && !status.is5xxServerError) {
                        log.warn { "openai 요청 거절 status=${status.value()} body=${e.responseBodyAsString}" }
                        throw OpenAiClientException("OpenAI 요청 실패 status=${status.value()}", e, status.value())
                    }
                    if (attempt >= BACKOFF.size) throw OpenAiClientException("OpenAI 재시도 소진 status=${status.value()}", e)
                    retryAfter(e.responseHeaders) ?: BACKOFF[attempt]
                } catch (e: ResourceAccessException) {
                    // 연결 실패 · 읽기 타임아웃
                    if (attempt >= BACKOFF.size) throw OpenAiClientException("OpenAI 재시도 소진: ${e.message}", e)
                    BACKOFF[attempt]
                }

            attempt++
            log.warn { "openai 재시도 $attempt/${BACKOFF.size} ${delay.toMillis()}ms 후" }
            sleeper(delay)
        }
    }

    private fun post(body: ByteArray): ByteArray =
        restClient
            .post()
            .uri(EMBEDDINGS_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body<ByteArray>() ?: throw OpenAiClientException("OpenAI 응답이 비었습니다.")

    // Retry-After: 초 단위 정수 또는 HTTP-date.
    private fun retryAfter(headers: HttpHeaders?): Duration? {
        val value = headers?.getFirst(HttpHeaders.RETRY_AFTER)?.trim() ?: return null
        value.toLongOrNull()?.let { return Duration.ofSeconds(it.coerceAtLeast(0)) }
        return runCatching {
            Duration
                .between(ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME))
                .takeIf { !it.isNegative } ?: Duration.ZERO
        }.getOrNull()
    }

    private fun parseResponse(body: ByteArray): OpenAiEmbeddingResponse =
        try {
            mapper.readValue(body, OpenAiEmbeddingResponse::class.java)
        } catch (e: JacksonException) {
            throw OpenAiClientException("OpenAI 응답 파싱 실패", e)
        }
}
