package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

import com.poppick.poppick.feature.popup.dataaccess.client.snakeCase
import com.poppick.poppick.feature.popup.domain.PerplexityEnrichResult
import com.poppick.poppick.feature.popup.domain.PopupEnrichment
import com.poppick.poppick.feature.popup.domain.SearchRecency
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
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
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }

@Component
class PerplexityAgentClient(
    jsonMapper: JsonMapper,
    @Value($$"${perplexity.api-key}")
    private val apiKey: String,
    @Value($$"${perplexity.base-url}")
    baseUrl: String,
    /** json_schema + web_search 조합이 동작하는 모델만 쓴다(anthropic 계열 모델 금지). */
    @Value($$"${perplexity.model}")
    private val model: String,
    @Value($$"${perplexity.read-timeout-seconds}")
    readTimeoutSeconds: Long,
) {
    companion object {
        private const val RESPONSES_PATH = "/v1/responses"
        private const val SCHEMA_NAME = "popup_enrichment"
        private const val INSTRUCTIONS_PATH = "prompts/popup-enrich-instructions.txt"
        private const val SCHEMA_PATH = "prompts/popup-enrich-schema.json"

        /** 429 · 5xx · 타임아웃 재시도 간격(Retry-After 가 있으면 그 값). 길이 = 최대 재시도 횟수. */
        private val BACKOFF = listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4))
        private val CONNECT_TIMEOUT = Duration.ofSeconds(10)
    }

    /** 테스트에서 MockRestServiceServer 에 바인딩한 RestClient 로 교체한다. */
    internal var restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build())
                    .apply { setReadTimeout(Duration.ofSeconds(readTimeoutSeconds)) },
            ).build()

    private val mapper = jsonMapper.snakeCase()
    private val instructions = ClassPathResource(INSTRUCTIONS_PATH).getContentAsString(UTF_8).trim()
    private val schema: Map<String, Any?> =
        ClassPathResource(SCHEMA_PATH).inputStream.use { mapper.readValue(it, object : TypeReference<Map<String, Any?>>() {}) }

    /** 재시도 대기. 테스트에서 교체한다. */
    internal var sleeper: (Duration) -> Unit = { Thread.sleep(it) }

    fun enrich(
        input: String,
        recency: SearchRecency,
    ): PerplexityEnrichResult {
        val request =
            PerplexityAgentRequest(
                model = model,
                input = input,
                instructions = instructions,
                tools = listOf(PerplexityAgentRequest.WebSearchTool(PerplexityAgentRequest.Filters(recency.value))),
                responseFormat = PerplexityAgentRequest.ResponseFormat(PerplexityAgentRequest.JsonSchema(SCHEMA_NAME, schema)),
            )

        val response = parseResponse(postWithRetry(mapper.writeValueAsBytes(request)))
        val cost = response.usage?.cost?.totalCost
        log.debug { "perplexity cost=$cost" }

        val enrichment = parseEnrichment(response.outputText())
        val searchResultUrls = response.searchResultUrls()
        log.debug { "perplexity reservation_url=${enrichment.reservationUrl} searchResultUrls=${searchResultUrls.size}" }

        return PerplexityEnrichResult(
            enrichment = enrichment,
            searchResultUrls = searchResultUrls,
            cost = cost,
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
                        log.warn { "perplexity 요청 거절 status=${status.value()} body=${e.responseBodyAsString}" }
                        throw PerplexityClientException("Perplexity 요청 실패 status=${status.value()}", e, status.value())
                    }
                    if (attempt >= BACKOFF.size) throw PerplexityClientException("Perplexity 재시도 소진 status=${status.value()}", e)
                    retryAfter(e.responseHeaders) ?: BACKOFF[attempt]
                } catch (e: ResourceAccessException) {
                    // 연결 실패 · 읽기 타임아웃
                    if (attempt >= BACKOFF.size) throw PerplexityClientException("Perplexity 재시도 소진: ${e.message}", e)
                    BACKOFF[attempt]
                }

            attempt++
            log.warn { "perplexity 재시도 $attempt/${BACKOFF.size} ${delay.toMillis()}ms 후" }
            sleeper(delay)
        }
    }

    private fun post(body: ByteArray): ByteArray =
        restClient
            .post()
            .uri(RESPONSES_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body<ByteArray>() ?: throw PerplexityClientException("Perplexity 응답이 비었습니다.")

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

    private fun parseResponse(body: ByteArray): PerplexityAgentResponse =
        try {
            mapper.readValue(body, PerplexityAgentResponse::class.java)
        } catch (e: JacksonException) {
            throw PerplexityClientException("Perplexity 응답 파싱 실패", e)
        }

    private fun parseEnrichment(text: String): PopupEnrichment {
        val json =
            text
                .trim()
                .removeSurrounding("```json", "```")
                .removeSurrounding("```", "```")
                .trim()
        if (json.isEmpty()) throw PerplexityClientException("Perplexity output_text 가 비었습니다.")

        return try {
            mapper.readValue(json, PopupEnrichment::class.java)
        } catch (e: JacksonException) {
            throw PerplexityClientException("Perplexity output_text JSON 파싱 실패: ${json.take(200)}", e)
        }
    }
}
