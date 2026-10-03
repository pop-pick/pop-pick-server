package com.poppick.poppick.feature.popup.dataaccess.client.web

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.domain.PageMeta
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.URI

class OgImageClientTest :
    FunSpec({
        val html = MediaType("text", "html", Charsets.UTF_8)

        class Fixture {
            private val builder = RestClient.builder()
            val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
            val client = OgImageClient(Fixtures.collectionProperties()).also { it.restClient = builder.build() }

            fun page(
                url: String,
                body: String,
            ) = server
                .expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, WebUrls.USER_AGENT))
                .andRespond(withSuccess(body, html))
        }

        fun head(meta: String) = "<html><head><title>사이트 제목</title>$meta</head><body>본문</body></html>"

        test("허용 출처 페이지의 og:image · og:title 을 읽는다") {
            val f = Fixture()
            f.page(
                "https://popga.co.kr/popup/123",
                head(
                    """<meta property="og:image" content="https://cdn.popga.co.kr/1.jpg">""" +
                        """<meta property="og:title" content="망그러진 곰 팝업스토어">""",
                ),
            )

            val meta = f.client.fetch("https://popga.co.kr/popup/123")

            f.server.verify()
            meta shouldBe PageMeta("https://cdn.popga.co.kr/1.jpg", "망그러진 곰 팝업스토어")
        }

        test("허용 host 가 아니거나 경로 접두사가 다르면 요청하지 않는다") {
            val f = Fixture()

            f.client.fetch("https://evil.example.com/popup/1").shouldBeNull()
            f.client.fetch("https://popga.co.kr.evil.com/popup/1").shouldBeNull()
            f.client.fetch("https://popga.co.kr/content/magazine").shouldBeNull()
            f.client.isAllowed("https://www.newsis.com/view/NISX2026") shouldBe true

            f.server.verify()
        }

        test("http · 비표준 포트 · userinfo URL 은 요청하지 않는다") {
            val f = Fixture()

            f.client.fetch("http://popga.co.kr/popup/123").shouldBeNull()
            f.client.fetch("https://popga.co.kr:8443/popup/123").shouldBeNull()
            f.client.fetch("https://user@popga.co.kr/popup/123").shouldBeNull()

            f.server.verify()
        }

        test("상대 경로 og:image 는 페이지 URL 기준 절대 URL 로 바꾸고, http 이미지는 버린다") {
            val f = Fixture()
            f.page("https://heypop.kr/n/55", head("""<meta property="og:image" content="/images/55.png">"""))
            f.page("https://heypop.kr/n/56", head("""<meta property="og:image" content="http://cdn.heypop.kr/56.png">"""))

            f.client.fetch("https://heypop.kr/n/55")?.imageUrl shouldBe "https://heypop.kr/images/55.png"
            f.client
                .fetch("https://heypop.kr/n/56")
                ?.imageUrl
                .shouldBeNull()
            f.server.verify()
        }

        test("og 태그가 없으면 twitter:image · title 로 대신하고, 둘 다 없으면 이미지는 null") {
            val f = Fixture()
            f.page("https://popply.co.kr/popup/1", head("""<meta name="twitter:image" content="https://cdn.popply.co.kr/1.jpg">"""))
            f.page("https://popply.co.kr/popup/2", head(""))

            f.client.fetch("https://popply.co.kr/popup/1") shouldBe PageMeta("https://cdn.popply.co.kr/1.jpg", "사이트 제목")
            f.client.fetch("https://popply.co.kr/popup/2") shouldBe PageMeta(null, "사이트 제목")
            f.server.verify()
        }

        test("본문은 앞 1MB 만 읽는다") {
            val f = Fixture()
            val padding = "<p>" + "가".repeat(OgImageClient.MAX_BODY_BYTES / 3) + "</p>"
            f.page(
                "https://popga.co.kr/popup/1",
                "<html><head><title>t</title></head><body>$padding" +
                    """<meta property="og:image" content="https://cdn.popga.co.kr/late.jpg"></body></html>""",
            )
            f.page(
                "https://popga.co.kr/popup/2",
                head("""<meta property="og:image" content="https://cdn.popga.co.kr/2.jpg">""").replace("본문", padding),
            )

            f.client
                .fetch("https://popga.co.kr/popup/1")
                ?.imageUrl
                .shouldBeNull()
            f.client.fetch("https://popga.co.kr/popup/2")?.imageUrl shouldBe "https://cdn.popga.co.kr/2.jpg"
            f.server.verify()
        }

        test("리다이렉트는 다음 위치가 허용 목록에 맞을 때만 따라간다") {
            val f = Fixture()
            f.server
                .expect(requestTo("https://popga.co.kr/popup/1"))
                .andRespond(withStatus(HttpStatus.MOVED_PERMANENTLY).location(URI("/popup/1/")))
            f.page("https://popga.co.kr/popup/1/", head("""<meta property="og:image" content="https://cdn.popga.co.kr/1.jpg">"""))
            f.server
                .expect(requestTo("https://popga.co.kr/popup/2"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI("https://evil.example.com/popup/2")))

            f.client.fetch("https://popga.co.kr/popup/1")?.imageUrl shouldBe "https://cdn.popga.co.kr/1.jpg"
            f.client.fetch("https://popga.co.kr/popup/2").shouldBeNull()
            f.server.verify()
        }

        test("HTTP 오류 · HTML 이 아닌 응답은 null") {
            val f = Fixture()
            f.server.expect(requestTo("https://popga.co.kr/popup/404")).andRespond(withStatus(HttpStatus.NOT_FOUND))
            f.server
                .expect(requestTo("https://popga.co.kr/popup/pdf"))
                .andRespond(withSuccess("%PDF", MediaType.APPLICATION_PDF))

            f.client.fetch("https://popga.co.kr/popup/404").shouldBeNull()
            f.client.fetch("https://popga.co.kr/popup/pdf").shouldBeNull()
            f.server.verify()
        }
    })
