package com.poppick.poppick.feature.popup.dataaccess.client.openai

import com.poppick.poppick.feature.popup.Fixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseActions
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Duration

class OpenAiEmbeddingClientTest :
    FunSpec({
        val url = "https://api.openai.com/v1/embeddings"

        class Setup(
            val client: OpenAiEmbeddingClient,
            val server: MockRestServiceServer,
            val sleeps: MutableList<Duration>,
        ) {
            fun expectRequest(count: ExpectedCount = ExpectedCount.once()): ResponseActions =
                server
                    .expect(count, requestTo(url))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))
        }

        // 테스트는 3차원으로 줄여 쓴다.
        fun setUp(): Setup {
            val builder = RestClient.builder().baseUrl("https://api.openai.com")
            val server = MockRestServiceServer.bindTo(builder).build()
            val client = OpenAiEmbeddingClient(Fixtures.jsonMapper, Fixtures.openAiProperties(embeddingDimensions = 3))
            client.restClient = builder.build()
            val sleeps = mutableListOf<Duration>()
            client.sleeper = { sleeps += it }
            return Setup(client, server, sleeps)
        }

        // index 역순으로 온 응답.
        val success =
            """
            {
              "object": "list",
              "data": [
                {"object": "embedding", "index": 1, "embedding": [0.4, 0.5, 0.6]},
                {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}
              ],
              "model": "text-embedding-3-small",
              "usage": {"prompt_tokens": 1500, "total_tokens": 1500}
            }
            """.trimIndent()

        test("정상 응답: 요청 본문을 보내고 벡터를 index 순으로 정렬, 토큰 · 비용을 계산한다") {
            val setup = setUp()
            setup
                .expectRequest()
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.input[0]").value("첫째"))
                .andExpect(jsonPath("$.input[1]").value("둘째"))
                .andExpect(jsonPath("$.dimensions").value(3))
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON))

            val result = setup.client.embed(listOf("첫째", "둘째"))

            setup.server.verify()
            result.vectors.map { it.toList() } shouldContainExactly listOf(listOf(0.1f, 0.2f, 0.3f), listOf(0.4f, 0.5f, 0.6f))
            result.promptTokens shouldBe 1500
            result.costUsd shouldBe (0.00003 plusOrMinus 1e-12)
        }

        test("벡터 차원이 설정과 다르면 예외") {
            val setup = setUp()
            setup
                .expectRequest()
                .andRespond(
                    withSuccess(
                        """{"data":[{"index":0,"embedding":[0.1,0.2]}],"usage":{"prompt_tokens":3}}""",
                        MediaType.APPLICATION_JSON,
                    ),
                )

            shouldThrow<OpenAiClientException> { setup.client.embed(listOf("첫째")) }
        }

        test("429 · 5xx 는 Retry-After 또는 지수 백오프로 재시도 후 성공한다") {
            val setup = setUp()
            setup.expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "5"))
            setup.expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
            setup.expectRequest().andRespond(withSuccess(success, MediaType.APPLICATION_JSON))

            setup.client
                .embed(listOf("첫째", "둘째"))
                .vectors.size shouldBe 2

            setup.server.verify()
            setup.sleeps shouldContainExactly listOf(Duration.ofSeconds(5), Duration.ofSeconds(2))
        }

        test("재시도 3회를 소진하면 예외") {
            val setup = setUp()
            setup.expectRequest(ExpectedCount.times(4)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))

            shouldThrow<OpenAiClientException> { setup.client.embed(listOf("첫째")) }

            setup.server.verify()
            setup.sleeps shouldContainExactly listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4))
        }

        test("400 은 재시도 없이 상태 코드를 담아 예외") {
            val setup = setUp()
            setup
                .expectRequest()
                .andRespond(withBadRequest().body("""{"error":{"message":"invalid dimensions"}}""").contentType(MediaType.APPLICATION_JSON))

            val exception = shouldThrow<OpenAiClientException> { setup.client.embed(listOf("첫째")) }

            setup.server.verify()
            setup.sleeps.shouldBeEmpty()
            exception.statusCode shouldBe 400
        }
    })
