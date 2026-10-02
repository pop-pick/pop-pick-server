package com.poppick.poppick.feature.popupdetail.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

private const val IP = "203.0.113.7"
private const val UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)"
private const val NGINX_IP = "172.18.0.5"

private fun anonymous(
    xRealIp: String? = null,
    xForwardedFor: String? = null,
    remoteAddr: String = NGINX_IP,
    userAgent: String? = UA,
) = PopupViewer.anonymous(xRealIp, xForwardedFor, remoteAddr, userAgent)

class PopupViewerTest :
    FunSpec({
        context("회원") {
            test("key 는 m:{memberKey}") {
                PopupViewer.member("abc").key shouldBe "m:abc"
            }

            test("같은 memberKey 면 같은 조회자, 다르면 다른 조회자") {
                PopupViewer.member("abc") shouldBe PopupViewer.member("abc")
                PopupViewer.member("abc") shouldNotBe PopupViewer.member("def")
            }

            test("memberKey 가 비어 있으면 예외") {
                shouldThrow<IllegalArgumentException> { PopupViewer.member(" ") }
            }
        }

        context("비회원") {
            test("key 는 a:{sha256 hex 64자} 이고 원본 IP · User-Agent 를 담지 않는다") {
                val key = anonymous(xRealIp = IP).key

                key shouldStartWith "a:"
                key shouldMatch Regex("a:[0-9a-f]{64}")
                key shouldNotContain IP
                key shouldNotContain "iPhone"
            }

            test("key 는 sha256(\"{ip}|{userAgent}\") 이다") {
                anonymous(xRealIp = IP, userAgent = "ua").key shouldBe
                    "a:" + sha256("$IP|ua")
            }

            test("같은 IP + User-Agent 면 같은 key") {
                anonymous(xRealIp = IP) shouldBe anonymous(xRealIp = IP)
            }

            test("IP 가 다르면 다른 key") {
                anonymous(xRealIp = IP) shouldNotBe anonymous(xRealIp = "203.0.113.8")
            }

            test("User-Agent 가 다르면 다른 key") {
                anonymous(xRealIp = IP) shouldNotBe anonymous(xRealIp = IP, userAgent = "Mozilla/5.0 (Android 15)")
            }

            test("User-Agent 가 없으면 빈 문자열로 해시한다") {
                anonymous(xRealIp = IP, userAgent = null) shouldBe anonymous(xRealIp = IP, userAgent = "")
            }

            test("회원과 비회원 key 는 겹치지 않는다") {
                PopupViewer.member("abc").key shouldStartWith "m:"
                anonymous(xRealIp = IP).key shouldStartWith "a:"
            }
        }

        context("클라이언트 IP 결정") {
            test("X-Real-IP 가 있으면 가장 먼저 쓴다") {
                PopupViewer.clientIp(" $IP ", "198.51.100.1, 198.51.100.2", NGINX_IP) shouldBe IP
            }

            test("X-Real-IP 가 없으면 X-Forwarded-For 의 맨 오른쪽 값을 쓴다(왼쪽은 위조 가능)") {
                PopupViewer.clientIp(null, "1.1.1.1, $IP", NGINX_IP) shouldBe IP
                PopupViewer.clientIp("  ", "$IP", NGINX_IP) shouldBe IP
            }

            test("X-Forwarded-For 의 빈 항목은 건너뛴다") {
                PopupViewer.clientIp(null, "$IP, , ", NGINX_IP) shouldBe IP
            }

            test("헤더가 모두 없거나 비어 있으면 remoteAddr 로 대체한다") {
                PopupViewer.clientIp(null, null, NGINX_IP) shouldBe NGINX_IP
                PopupViewer.clientIp("", " , ", NGINX_IP) shouldBe NGINX_IP
            }

            test("같은 사용자는 IP 를 어느 헤더로 받아도 같은 key") {
                anonymous(xRealIp = IP) shouldBe anonymous(xForwardedFor = IP)
                anonymous(xForwardedFor = IP) shouldBe anonymous(remoteAddr = IP)
            }
        }
    })

private fun sha256(value: String): String =
    java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
