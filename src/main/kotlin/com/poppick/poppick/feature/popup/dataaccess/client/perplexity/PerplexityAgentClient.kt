package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

import com.poppick.poppick.config.properties.PerplexityProperties
import com.poppick.poppick.feature.popup.dataaccess.client.snakeCase
import com.poppick.poppick.feature.popup.domain.PerplexityEnrichResult
import com.poppick.poppick.feature.popup.domain.PerplexityImageResult
import com.poppick.poppick.feature.popup.domain.PerplexityUsage
import com.poppick.poppick.feature.popup.domain.PopupEnrichment
import com.poppick.poppick.feature.popup.domain.SearchRecency
import io.github.oshai.kotlinlogging.KotlinLogging
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
import tools.jackson.databind.node.ObjectNode
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }

@Component
class PerplexityAgentClient(
    jsonMapper: JsonMapper,
    private val properties: PerplexityProperties,
) {
    companion object {
        private const val RESPONSES_PATH = "/v1/responses"
        private const val SCHEMA_NAME = "popup_enrichment"
        private const val INSTRUCTIONS_PATH = "prompts/popup-enrich-instructions.txt"
        private const val SCHEMA_PATH = "prompts/popup-enrich-schema.json"
        private const val MATCHES_PLACE = "matches_place"

        private const val IMAGE_SCHEMA_NAME = "popup_image"
        private const val IMAGE_INSTRUCTIONS_PATH = "prompts/popup-image-instructions.txt"
        private const val IMAGE_SCHEMA_PATH = "prompts/popup-image-schema.json"
        private const val IMAGE_URL = "image_url"
        private const val IMAGE_MAX_RESULTS = 5

        /** 응답은 {"image_url": ...} 하나라 작게 둔다. 추론 토큰이 이 한도에 포함되는 모델이면 잘릴 수 있다. */
        private const val IMAGE_MAX_OUTPUT_TOKENS = 1000

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
    private val instructions = readText(INSTRUCTIONS_PATH)
    private val schema = readSchema(SCHEMA_PATH)
    private val imageInstructions = readText(IMAGE_INSTRUCTIONS_PATH)
    private val imageSchema = readSchema(IMAGE_SCHEMA_PATH)

    /** 재시도 대기. 테스트에서 교체한다. */
    internal var sleeper: (Duration) -> Unit = { Thread.sleep(it) }

    fun enrich(
        input: String,
        recency: SearchRecency,
    ): PerplexityEnrichResult {
        val request =
            PerplexityAgentRequest(
                model = properties.model,
                input = input,
                instructions = instructions,
                tools = listOf(PerplexityAgentRequest.WebSearchTool(PerplexityAgentRequest.Filters(recency.value))),
                responseFormat = PerplexityAgentRequest.ResponseFormat(PerplexityAgentRequest.JsonSchema(SCHEMA_NAME, schema)),
            )

        val response = call(request)
        val usage = response.toUsage()
        val enrichment = parseEnrichment(response.outputText(), usage)
        val searchResultUrls = response.searchResultUrls()
        log.debug { "perplexity reservation_url=${enrichment.reservationUrl} searchResultUrls=${searchResultUrls.size}" }

        return PerplexityEnrichResult(
            enrichment = enrichment,
            searchResultUrls = searchResultUrls,
            usage = usage,
        )
    }

    /**
     * 팝업 대표 이미지 1장을 모델에게 고르게 한다(image_search 도구만, web_search 없음).
     * 반환한 imageUrl 은 검증 전 값이다. candidates(실제 검색 결과)에 있는지 · 이미지인지는 호출 측이 확인한다.
     * 재시도 · 잘린 응답 · 파싱 실패 처리는 enrich 와 같다.
     */
    fun findImage(input: String): PerplexityImageResult {
        val request =
            PerplexityAgentRequest(
                model = properties.model,
                input = input,
                instructions = imageInstructions,
                tools = listOf(PerplexityAgentRequest.ImageSearchTool(maxResults = IMAGE_MAX_RESULTS)),
                responseFormat = PerplexityAgentRequest.ResponseFormat(PerplexityAgentRequest.JsonSchema(IMAGE_SCHEMA_NAME, imageSchema)),
                maxOutputTokens = IMAGE_MAX_OUTPUT_TOKENS,
            )

        val response = call(request)
        val usage = response.toUsage()
        val candidates = response.imageSearchResultUrls()
        val imageUrl =
            try {
                mapper
                    .readTree(jsonText(response.outputText(), usage))
                    .get(IMAGE_URL)
                    ?.takeIf { it.isString }
                    ?.asString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            } catch (e: JacksonException) {
                throw PerplexityClientException("Perplexity output_text JSON 파싱 실패: ${response.outputText().take(200)}", e, usage = usage)
            }
        log.debug { "perplexity image_url=$imageUrl candidates=${candidates.size}" }

        return PerplexityImageResult(imageUrl = imageUrl, candidates = candidates, usage = usage)
    }

    /** 요청 → 응답 파싱. 잘린 응답은 JSON 이 불완전하므로 파싱하지 않고 실패로 보낸다(사유는 호출 측이 popupId 와 함께 WARN). */
    private fun call(request: PerplexityAgentRequest): PerplexityAgentResponse {
        val response = parseResponse(postWithRetry(mapper.writeValueAsBytes(request)))
        if (response.isIncomplete()) {
            val reason = response.incompleteDetails?.reason
            throw PerplexityClientException(
                "Perplexity 응답 잘림 reason=$reason",
                usage = response.toUsage(),
                incompleteReason = reason ?: "unknown",
            )
        }
        return response
    }

    private fun readText(path: String) = ClassPathResource(path).getContentAsString(UTF_8).trim()

    private fun readSchema(path: String): Map<String, Any?> =
        ClassPathResource(path).inputStream.use { mapper.readValue(it, object : TypeReference<Map<String, Any?>>() {}) }

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
            .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
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

    /** 코드 펜스를 벗긴 output_text. 비어 있으면 실패. */
    private fun jsonText(
        text: String,
        usage: PerplexityUsage,
    ): String {
        val json =
            text
                .trim()
                .removeSurrounding("```json", "```")
                .removeSurrounding("```", "```")
                .trim()
        if (json.isEmpty()) throw PerplexityClientException("Perplexity output_text 가 비었습니다.", usage = usage)
        return json
    }

    /**
     * output_text 를 PopupEnrichment 로 읽는다.
     * matches_place 는 스키마 required 라 항상 오지만, 빠지거나 null 이면 true 로 읽는다(누락만으로 정보를 버리지 않게).
     */
    private fun parseEnrichment(
        text: String,
        usage: PerplexityUsage,
    ): PopupEnrichment {
        val json = jsonText(text, usage)
        return try {
            val node = mapper.readTree(json)
            if (node is ObjectNode && !node.hasNonNull(MATCHES_PLACE)) node.put(MATCHES_PLACE, true)
            mapper.treeToValue(node, PopupEnrichment::class.java)
        } catch (e: JacksonException) {
            throw PerplexityClientException("Perplexity output_text JSON 파싱 실패: ${json.take(200)}", e, usage = usage)
        }
    }
}
