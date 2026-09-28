package com.poppick.poppick.feature.popup.dataaccess.client.openai

import com.poppick.poppick.config.properties.OpenAiProperties
import com.poppick.poppick.feature.popup.dataaccess.client.snakeCase
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }

/**
 * OpenAI Responses API(Structured Outputs, strict JSON schema) 호출. output_text 원문(JSON 문자열)을 돌려준다.
 * 응답 JSON 의 해석 · 검증은 호출 측 몫이다.
 */
@Component
class OpenAiChatClient(
    jsonMapper: JsonMapper,
    private val properties: OpenAiProperties,
) {
    companion object {
        private const val RESPONSES_PATH = "/v1/responses"

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
                    .apply { setReadTimeout(Duration.ofSeconds(properties.chatReadTimeoutSeconds)) },
            ).build()

    private val mapper = jsonMapper.snakeCase()

    /** 재시도 대기. 테스트에서 교체한다. */
    internal var sleeper: (Duration) -> Unit = { Thread.sleep(it) }

    fun complete(
        instructions: String,
        input: String,
        schemaName: String,
        schema: JsonNode,
    ): String {
        val request =
            OpenAiChatRequest(
                model = properties.chatModel,
                instructions = instructions,
                input = input,
                text = OpenAiChatRequest.Text(OpenAiChatRequest.Format(name = schemaName, schema = schema)),
                maxOutputTokens = properties.chatMaxOutputTokens,
            )
        val body = mapper.writeValueAsBytes(request)
        log.debug { "openai chat 요청 ${String(body)}" }

        val responseBody = postWithRetry(body)
        log.debug { "openai chat 응답 ${String(responseBody)}" }
        val response = parseResponse(responseBody)
        log.info {
            "openai chat usage: model=${properties.chatModel} schema=$schemaName " +
                "input_tokens=${response.usage?.inputTokens} output_tokens=${response.usage?.outputTokens}"
        }

        // 잘린 응답은 JSON 이 불완전하므로 돌려주지 않는다.
        if (response.status != OpenAiChatResponse.STATUS_COMPLETED) {
            throw OpenAiClientException("OpenAI 응답 미완료 status=${response.status} reason=${response.incompleteDetails?.reason}")
        }
        return response.outputText().takeIf { it.isNotBlank() }
            ?: throw OpenAiClientException("OpenAI output_text 가 없습니다.")
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
                        log.warn { "openai chat 요청 거절 status=${status.value()} body=${e.responseBodyAsString}" }
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
            log.warn { "openai chat 재시도 $attempt/${BACKOFF.size} ${delay.toMillis()}ms 후" }
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

    private fun parseResponse(body: ByteArray): OpenAiChatResponse =
        try {
            mapper.readValue(body, OpenAiChatResponse::class.java)
        } catch (e: JacksonException) {
            throw OpenAiClientException("OpenAI 응답 파싱 실패", e)
        }
}
