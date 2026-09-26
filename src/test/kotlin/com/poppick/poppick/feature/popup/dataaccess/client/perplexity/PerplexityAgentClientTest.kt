package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.domain.PerplexityUsage
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SearchRecency
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
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

class PerplexityAgentClientTest :
    FunSpec({
        val url = "https://api.perplexity.ai/v1/responses"

        class Setup(
            val client: PerplexityAgentClient,
            val server: MockRestServiceServer,
            val sleeps: MutableList<Duration>,
        ) {
            fun expectRequest(count: ExpectedCount = ExpectedCount.once()): ResponseActions =
                server
                    .expect(count, requestTo(url))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-pplx-key"))
        }

        fun setUp(): Setup {
            val builder = RestClient.builder().baseUrl("https://api.perplexity.ai")
            val server = MockRestServiceServer.bindTo(builder).build()
            val client =
                PerplexityAgentClient(
                    jsonMapper = Fixtures.jsonMapper,
                    properties = Fixtures.perplexityProperties(),
                )
            client.restClient = builder.build()
            val sleeps = mutableListOf<Duration>()
            client.sleeper = { sleeps += it }
            return Setup(client, server, sleeps)
        }

        val success = Fixtures.read("perplexity/enrich-success.json")

        test("정상 응답: output_text 를 PopupEnrichment 로, search_results 항목 전부를 URL 목록으로 모은다") {
            val setup = setUp()
            setup
                .expectRequest()
                .andExpect(jsonPath("$.model").value("openai/gpt-6-luna"))
                .andExpect(jsonPath("$.input").value("입력"))
                .andExpect(jsonPath("$.tools[0].type").value("web_search"))
                .andExpect(jsonPath("$.tools[0].search_context_size").value("medium"))
                .andExpect(jsonPath("$.tools[0].max_results").value(20))
                .andExpect(jsonPath("$.tools[0].filters.search_recency_filter").value("year"))
                .andExpect(jsonPath("$.tools[0].filters.search_domain_filter[0]").value("-popupkorea.co.kr"))
                .andExpect(jsonPath("$.tools[0].user_location.country").value("KR"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.name").value("popup_enrichment"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required[1]").value("matches_place"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.matches_place.type").value("boolean"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.is_popup").doesNotExist())
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.opening_hours.type[0]").value("string"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.properties.opening_hours.type[1]").value("null"))
                .andExpect(jsonPath("$.max_output_tokens").value(1500))
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON))

            val result = setup.client.enrich("입력", SearchRecency.YEAR)

            setup.server.verify()
            with(result.enrichment) {
                found shouldBe true
                matchesPlace shouldBe true
                title shouldBe "망그러진 곰 팝업스토어"
                interestCategory shouldBe "캐릭터/IP"
                startDate shouldBe "2026-09-10"
                openingHours shouldBe "매일 11:00~20:00, 월 휴무"
                reservationType shouldBe ReservationType.BOTH
                entryFee shouldBe 0
            }
            result.searchResultUrls shouldContainExactly
                listOf(
                    "https://www.instagram.com/p/abc",
                    "https://blog.naver.com/popup/1",
                    "https://booking.naver.com/booking/6/bizes/123",
                )
            result.usage shouldBe PerplexityUsage(costUsd = 0.013, inputTokens = 1200, outputTokens = 300, searchCalls = 2)
        }

        test("재시도 건(NONE)은 search_recency_filter 키 자체를 보내지 않는다") {
            val setup = setUp()
            setup
                .expectRequest()
                .andExpect(jsonPath("$.tools[0].filters.search_recency_filter").doesNotExist())
                .andExpect(jsonPath("$.tools[0].filters.search_domain_filter[0]").value("-popupkorea.co.kr"))
                .andRespond(withSuccess(success, MediaType.APPLICATION_JSON))

            setup.client.enrich("입력", SearchRecency.NONE)

            setup.server.verify()
        }

        test("usage 가 없으면 비용 · 검색 횟수 0, 토큰 NULL") {
            val setup = setUp()
            setup.expectRequest().andRespond(withSuccess(Fixtures.read("perplexity/enrich-no-usage.json"), MediaType.APPLICATION_JSON))

            setup.client.enrich("입력", SearchRecency.YEAR).usage shouldBe PerplexityUsage()
        }

        test("status=incomplete 응답은 파싱하지 않고 사유 · 사용량을 담아 예외") {
            val setup = setUp()
            setup.expectRequest().andRespond(withSuccess(Fixtures.read("perplexity/enrich-incomplete.json"), MediaType.APPLICATION_JSON))

            val exception = shouldThrow<PerplexityClientException> { setup.client.enrich("입력", SearchRecency.YEAR) }

            exception.incompleteReason shouldBe "max_output_tokens"
            exception.usage shouldBe PerplexityUsage(costUsd = 0.02, inputTokens = 1300, outputTokens = 1500, searchCalls = 3)
            exception.isClientError shouldBe false
        }

        test("matches_place 가 없거나 null 이면 true 로 읽는다") {
            listOf("perplexity/enrich-missing-matches-place.json", "perplexity/enrich-null-matches-place.json").forEach { fixture ->
                val setup = setUp()
                setup.expectRequest().andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON))

                setup.client
                    .enrich("입력", SearchRecency.YEAR)
                    .enrichment.matchesPlace shouldBe true
            }
        }

        test("429 · 5xx 는 Retry-After 또는 지수 백오프로 재시도 후 성공한다") {
            val setup = setUp()
            setup.expectRequest().andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "5"))
            setup.expectRequest().andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
            setup.expectRequest().andRespond(withSuccess(success, MediaType.APPLICATION_JSON))

            val result = setup.client.enrich("입력", SearchRecency.YEAR)

            setup.server.verify()
            result.enrichment.found shouldBe true
            setup.sleeps shouldContainExactly listOf(Duration.ofSeconds(5), Duration.ofSeconds(2))
        }

        test("재시도 3회를 소진하면 예외") {
            val setup = setUp()
            setup.expectRequest(ExpectedCount.times(4)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

            shouldThrow<PerplexityClientException> { setup.client.enrich("입력", SearchRecency.YEAR) }

            setup.server.verify()
            setup.sleeps shouldContainExactly listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4))
        }

        test("400 은 재시도 없이 예외") {
            val setup = setUp()
            setup
                .expectRequest()
                .andRespond(withBadRequest().body("""{"error":{"message":"invalid model"}}""").contentType(MediaType.APPLICATION_JSON))

            val exception = shouldThrow<PerplexityClientException> { setup.client.enrich("입력", SearchRecency.YEAR) }

            setup.server.verify()
            setup.sleeps.shouldBeEmpty()
            exception.statusCode shouldBe 400
            exception.isClientError shouldBe true
        }

        test("output_text 가 JSON 이 아니면 예외") {
            val setup = setUp()
            setup.expectRequest().andRespond(withSuccess(Fixtures.read("perplexity/enrich-not-json.json"), MediaType.APPLICATION_JSON))

            shouldThrow<PerplexityClientException> { setup.client.enrich("입력", SearchRecency.YEAR) }
        }
    })
