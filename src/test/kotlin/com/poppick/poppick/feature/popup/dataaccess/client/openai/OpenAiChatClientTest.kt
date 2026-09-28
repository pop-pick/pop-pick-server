package com.poppick.poppick.feature.popup.dataaccess.client.openai

import com.poppick.poppick.feature.popup.Fixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

class OpenAiChatClientTest :
    FunSpec({
        val url = "https://api.openai.com/v1/responses"
        val schema =
            Fixtures.jsonMapper.readTree(
                """{"type": "object", "properties": {"title": {"type": "string"}}, "required": ["title"], "additionalProperties": false}""",
            )

        class Setup(
            val client: OpenAiChatClient,
            val server: MockRestServiceServer,
            val sleeps: MutableList<Duration>,
        ) {
            fun expectRequest(count: ExpectedCount = ExpectedCount.once()): ResponseActions =
                server
                    .expect(count, requestTo(url))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-openai-key"))

            fun complete() = client.complete("지시문", "입력", "planner_course", schema)
        }

        fun setUp(): Setup {
            val builder = RestClient.builder().baseUrl("https://api.openai.com")
            val server = MockRestServiceServer.bindTo(builder).build()
            val client = OpenAiChatClient(Fixtures.jsonMapper, Fixtures.openAiProperties())
            client.restClient = builder.build()
            val sleeps = mutableListOf<Duration>()
            client.sleeper = { sleeps += it }
            return Setup(client, server, sleeps)
        }

        fun response(
            status: String = "completed",
            text: String? = """{\"title\": \"성수 코스\"}""",
            incompleteReason: String? = null,
        ): String {
            val incompleteDetails = incompleteReason?.let { """{"reason": "$it"}""" } ?: "null"
            val content = text?.let { """{"type": "output_text", "text": "$it", "annotations": []}""" }.orEmpty()
            return """
                {
                  "id": "resp_1",
                  "status": "$status",
                  "incomplete_details": $incompleteDetails,
                  "output": [
                    {"type": "reasoning", "summary": []},
                    {"type": "message", "role": "assistant", "content": [$content]}
                  ],
                  "usage": {"input_tokens": 1200, "output_tokens": 300, "total_tokens": 1500}
                }
                """.trimIndent()
        }

        test("요청 본문: model · instructions · input · strict json_schema · temperature · max_output_tokens, 응답의 output_text 를 돌려준다") {
            val setup = setUp()
            setup
                .expectRequest()
                .andExpect(jsonPath("$.model").value("gpt-4.1-mini"))
                .andExpect(jsonPath("$.instructions").value("지시문"))
                .andExpect(jsonPath("$.input").value("입력"))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andExpect(jsonPath("$.text.format.name").value("planner_course"))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andExpect(jsonPath("$.text.format.schema.additionalProperties").value(false))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andExpect(jsonPath("$.max_output_tokens").value(1024))
                .andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))

            val text = setup.complete()

            setup.server.verify()
            text shouldBe """{"title": "성수 코스"}"""
        }

        test("status=incomplete(max_output_tokens) 면 예외") {
            val setup = setUp()
            setup.expectRequest().andRespond(
                withSuccess(response(status = "incomplete", incompleteReason = "max_output_tokens"), MediaType.APPLICATION_JSON),
            )

            shouldThrow<OpenAiClientException> { setup.complete() }.message!! shouldContain "reason=max_output_tokens"
        }

        test("output_text 가 없으면 예외") {
            val setup = setUp()
            setup.expectRequest().andRespond(withSuccess(response(text = null), MediaType.APPLICATION_JSON))

            shouldThrow<OpenAiClientException> { setup.complete() }.message!! shouldContain "output_text"
        }

        test("429 는 재시도하고 성공한다") {
            val setup = setUp()
            setup.expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))
            setup.expectRequest().andRespond(withSuccess(response(), MediaType.APPLICATION_JSON))

            setup.complete() shouldBe """{"title": "성수 코스"}"""
            setup.sleeps shouldBe listOf(Duration.ofSeconds(1))
        }

        test("4xx 는 재시도 없이 상태 코드를 담아 예외") {
            val setup = setUp()
            setup.expectRequest().andRespond(withBadRequest())

            shouldThrow<OpenAiClientException> { setup.complete() }.statusCode shouldBe 400
            setup.server.verify()
        }
    })
