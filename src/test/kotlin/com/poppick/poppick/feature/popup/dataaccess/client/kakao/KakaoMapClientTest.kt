package com.poppick.poppick.feature.popup.dataaccess.client.kakao

import com.poppick.poppick.feature.popup.Fixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.startsWith
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class KakaoMapClientTest :
    FunSpec({
        fun setUp(maxPage: Int = 3): Pair<KakaoMapClient, MockRestServiceServer> {
            val builder = RestClient.builder().baseUrl("https://dapi.kakao.com")
            val server = MockRestServiceServer.bindTo(builder).build()
            val client =
                KakaoMapClient(
                    jsonMapper = Fixtures.jsonMapper,
                    properties = Fixtures.kakaoMapProperties(maxPage),
                    apiKey = "test-key",
                )
            client.restClient = builder.build()
            return client to server
        }

        fun MockRestServiceServer.expectPage(
            page: Int,
            fixture: String,
        ) = expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
            .andExpect(queryParam("page", page.toString()))
            .andExpect(queryParam("size", "15"))
            .andExpect(queryParam("rect", "126.764,37.413,127.184,37.715"))
            .andRespond(withSuccess(Fixtures.read(fixture), MediaType.APPLICATION_JSON))

        test("is_end 가 true 인 페이지까지 조회하고 document 를 KakaoPlace 로 매핑한다") {
            val (client, server) = setUp()
            server.expectPage(1, "kakao/keyword-page1.json")
            server.expectPage(2, "kakao/keyword-page2.json")

            val places = client.searchAll("성수동 팝업스토어")

            server.verify()
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
        }

        test("maxPage 에 닿으면 is_end 가 false 여도 중단한다") {
            val (client, server) = setUp(maxPage = 1)
            server.expectPage(1, "kakao/keyword-page1.json")

            val places = client.searchAll("성수동 팝업스토어")

            server.verify()
            places.size shouldBe 2
        }
    })
