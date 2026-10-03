package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.feature.popup.Fixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.startsWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.ResponseCreator
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

class KakaoMapClientTest :
    FunSpec({
        val root = "126.764,37.413,127.184,37.715"
        // root 를 4등분한 칸(좌하 · 우하 · 좌상 · 우상)
        val quarters =
            listOf(
                "126.764,37.413,126.974,37.564",
                "126.974,37.413,127.184,37.564",
                "126.764,37.564,126.974,37.715",
                "126.974,37.564,127.184,37.715",
            )

        class Fixture(
            maxPage: Int = 3,
            maxSplitDepth: Int = 6,
        ) {
            private val builder = RestClient.builder().baseUrl("https://dapi.kakao.com")
            val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
            var sleeps = 0
            val client =
                KakaoMapClient(
                    jsonMapper = Fixtures.jsonMapper,
                    properties = Fixtures.kakaoMapProperties(maxPage, maxSplitDepth),
                    apiKey = "test-key",
                ).also {
                    it.restClient = builder.build()
                    it.sleeper = { sleeps++ }
                }

            fun expect(
                page: Int,
                response: ResponseCreator,
                rect: String = root,
            ) = server
                .expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andExpect(queryParam("page", page.toString()))
                .andExpect(queryParam("size", "15"))
                .andExpect(queryParam("rect", rect))
                .andRespond(response)
        }

        fun doc(id: String) =
            """{"id":"$id","place_name":"팝업$id","address_name":"서울 성동구 성수동2가 $id","road_address_name":"",""" +
                """"x":"127.05","y":"37.54","place_url":"http://place.map.kakao.com/$id"}"""

        fun page(
            ids: List<String>,
            isEnd: Boolean,
            total: Int? = ids.size,
            pageable: Int? = total,
        ): ResponseCreator {
            val counts = listOfNotNull(total?.let { "\"total_count\":$it" }, pageable?.let { "\"pageable_count\":$it" })
            val meta = (listOf("\"is_end\":$isEnd") + counts).joinToString(",")
            return withSuccess("""{"meta":{$meta},"documents":[${ids.joinToString(",") { doc(it) }}]}""", MediaType.APPLICATION_JSON)
        }

        fun ids(range: IntRange) = range.map { it.toString() }

        fun fixture(path: String) = withSuccess(Fixtures.read(path), MediaType.APPLICATION_JSON)

        test("is_end 가 true 인 페이지까지 조회하고 document 를 KakaoPlace 로 매핑한다") {
            val f = Fixture()
            f.expect(1, fixture("kakao/keyword-page1.json"))
            f.expect(2, fixture("kakao/keyword-page2.json"))

            val result = f.client.searchAll("성수동 팝업스토어")

            f.server.verify()
            val places = result.places
            places.map { it.id } shouldContainExactly listOf("1001", "1002", "1003")
            with(places[0]) {
                placeName shouldBe "성수 캐릭터 팝업"
                categoryName shouldBe "가정,생활 > 팝업스토어"
                addressJibun shouldBe "서울 성동구 성수동2가 322-1"
                addressRoad shouldBe "서울 성동구 연무장길 10"
                latitude shouldBe 37.5432
                longitude shouldBe 127.0567
                placeUrl shouldBe "http://place.map.kakao.com/1001"
                phone.shouldBeNull()
                raw["category_name"] shouldBe "가정,생활 > 팝업스토어"
            }
            places[2].addressJibun.shouldBeNull()
            places[2].isInSeoul() shouldBe true
            places[1].isInSeoul() shouldBe false
            result.partial shouldBe false
            result.truncated shouldBe false
        }

        test("maxPage 에 닿으면 is_end 가 false 여도 중단한다") {
            val f = Fixture(maxPage = 1)
            f.expect(1, fixture("kakao/keyword-page1.json"))

            val result = f.client.searchAll("성수동 팝업스토어")

            f.server.verify()
            result.places.size shouldBe 2
        }

        test("total_count > pageable_count 면 2 · 3페이지 대신 4칸으로 나눠 재조회하고 place id 로 중복을 제거한다") {
            val f = Fixture(maxSplitDepth = 1)
            f.expect(1, page(ids(1..15), isEnd = false, total = 120, pageable = 45))
            f.expect(1, page(listOf("1", "2"), isEnd = true), quarters[0])
            f.expect(1, page(listOf("2", "16"), isEnd = true), quarters[1])
            f.expect(1, page(emptyList(), isEnd = true), quarters[2])
            f.expect(1, page(listOf("17"), isEnd = false, total = 2), quarters[3])
            f.expect(2, page(listOf("18"), isEnd = true, total = 2), quarters[3])

            val result = f.client.searchAll("팝업스토어")

            f.server.verify()
            result.places.map { it.id } shouldContainExactly ids(1..18)
            result.truncated shouldBe false
            result.partial shouldBe false
            // 분할 호출 포함 모든 요청 사이에 대기(요청 6건 → 5번)
            f.sleeps shouldBe 5
        }

        test("분할 한도에서도 잘리면 그 칸은 45건(3페이지)만 받고 truncated 로 표시한다") {
            val f = Fixture(maxSplitDepth = 0)
            f.expect(1, page(ids(1..15), isEnd = false, total = 120, pageable = 45))
            f.expect(2, page(ids(16..30), isEnd = false, total = 120, pageable = 45))
            f.expect(3, page(ids(31..45), isEnd = false, total = 120, pageable = 45))

            val result = f.client.searchAll("팝업스토어")

            f.server.verify()
            result.places.size shouldBe 45
            result.truncated shouldBe true
        }

        test("total_count · pageable_count 가 없으면 maxPage 까지 받고 is_end=false 로 잘림을 판단한다") {
            val f = Fixture(maxSplitDepth = 0)
            f.expect(1, page(ids(1..15), isEnd = false, total = null))
            f.expect(2, page(ids(16..30), isEnd = false, total = null))
            f.expect(3, page(ids(31..45), isEnd = false, total = null))

            val result = f.client.searchAll("팝업스토어")

            f.server.verify()
            result.places.size shouldBe 45
            result.truncated shouldBe true
        }

        test("5xx · 429 응답은 1회 재시도해 성공하면 그대로 이어간다") {
            val f = Fixture()
            f.expect(1, withServerError())
            f.expect(1, fixture("kakao/keyword-page1.json"))
            f.expect(2, withStatus(HttpStatus.TOO_MANY_REQUESTS))
            f.expect(2, fixture("kakao/keyword-page2.json"))

            val result = f.client.searchAll("성수동 팝업스토어")

            f.server.verify()
            result.places.map { it.id } shouldContainExactly listOf("1001", "1002", "1003")
            result.partial shouldBe false
            // 재시도 전에도 대기한다
            f.sleeps shouldBe 3
        }

        test("2페이지가 재시도까지 실패하면 1페이지 결과를 partial 로 반환한다") {
            val f = Fixture()
            f.expect(1, fixture("kakao/keyword-page1.json"))
            f.expect(2, withServerError())
            f.expect(2, withServerError())

            val result = f.client.searchAll("성수동 팝업스토어")

            f.server.verify()
            result.places.map { it.id } shouldContainExactly listOf("1001", "1002")
            result.partial shouldBe true
        }

        test("분할 칸이 재시도까지 실패하면 그때까지 모은 결과를 partial 로 반환한다") {
            val f = Fixture(maxSplitDepth = 1)
            f.expect(1, page(ids(1..15), isEnd = false, total = 120, pageable = 45))
            f.expect(1, page(listOf("16"), isEnd = true), quarters[0])
            f.expect(1, withServerError(), quarters[1])
            f.expect(1, withServerError(), quarters[1])

            val result = f.client.searchAll("팝업스토어")

            f.server.verify()
            result.places.map { it.id } shouldContainExactly ids(1..16)
            result.partial shouldBe true
        }

        test("첫 요청이 재시도까지 실패하면 예외를 던진다") {
            val f = Fixture()
            f.expect(1, withServerError())
            f.expect(1, withServerError())

            shouldThrow<HttpServerErrorException> { f.client.searchAll("성수동 팝업스토어") }
            f.server.verify()
        }

        test("429 가 아닌 4xx 는 재시도하지 않는다") {
            val f = Fixture()
            f.expect(1, withStatus(HttpStatus.BAD_REQUEST))

            shouldThrow<HttpClientErrorException> { f.client.searchAll("성수동 팝업스토어") }
            f.server.verify()
        }
    })
