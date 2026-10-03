package com.poppick.poppick.feature.popup.dataaccess.client.web

import io.kotest.core.spec.style.FunSpec
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
import org.springframework.web.client.RestClient
import java.net.InetAddress
import java.net.URI

class ImageProbeClientTest :
    FunSpec({
        val publicAddress = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

        class Fixture {
            private val builder = RestClient.builder()
            val server: MockRestServiceServer = MockRestServiceServer.bindTo(builder).build()
            val client =
                ImageProbeClient().also {
                    it.restClient = builder.build()
                    it.resolver =
                        { host -> if (host == "internal.example.com") listOf(InetAddress.getLoopbackAddress()) else listOf(publicAddress) }
                }
        }

        test("HEAD 응답의 Content-Type 이 image/… 이면 true, 아니면 false") {
            val f = Fixture()
            f.server
                .expect(requestTo("https://img.example.com/1.jpg"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.IMAGE_JPEG))
            f.server
                .expect(requestTo("https://img.example.com/2.jpg"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_HTML))

            f.client.isImage("https://img.example.com/1.jpg") shouldBe true
            f.client.isImage("https://img.example.com/2.jpg") shouldBe false
            f.server.verify()
        }

        test("HEAD 가 405 면 Range GET 으로 다시 확인한다") {
            val f = Fixture()
            f.server
                .expect(requestTo("https://img.example.com/1.png"))
                .andExpect(method(HttpMethod.HEAD))
                .andRespond(withStatus(HttpStatus.METHOD_NOT_ALLOWED))
            f.server
                .expect(requestTo("https://img.example.com/1.png"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.RANGE, "bytes=0-0"))
                .andRespond(withStatus(HttpStatus.PARTIAL_CONTENT).contentType(MediaType.IMAGE_PNG))

            f.client.isImage("https://img.example.com/1.png") shouldBe true
            f.server.verify()
        }

        test("리다이렉트는 https 공개 주소일 때만 따라간다") {
            val f = Fixture()
            f.server
                .expect(requestTo("https://img.example.com/r"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI("https://cdn.example.com/r.jpg")))
            f.server
                .expect(requestTo("https://cdn.example.com/r.jpg"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.IMAGE_JPEG))
            f.server
                .expect(requestTo("https://img.example.com/internal"))
                .andRespond(withStatus(HttpStatus.FOUND).location(URI("https://internal.example.com/x.jpg")))

            f.client.isImage("https://img.example.com/r") shouldBe true
            f.client.isImage("https://img.example.com/internal") shouldBe false
            f.server.verify()
        }

        test("http · 사설 주소 · 오류 응답은 false 이고 요청하지 않거나 예외를 던지지 않는다") {
            val f = Fixture()
            f.server.expect(requestTo("https://img.example.com/404.jpg")).andRespond(withStatus(HttpStatus.NOT_FOUND))

            f.client.isImage("http://img.example.com/1.jpg") shouldBe false
            f.client.isImage("https://internal.example.com/1.jpg") shouldBe false
            f.client.isImage("https://img.example.com/404.jpg") shouldBe false
            f.client.isImage("not a url") shouldBe false
            f.server.verify()
        }
    })
