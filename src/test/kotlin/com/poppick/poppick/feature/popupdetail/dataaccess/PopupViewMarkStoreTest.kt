package com.poppick.poppick.feature.popupdetail.dataaccess

import com.poppick.poppick.feature.popup.LogCapture
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration

class PopupViewMarkStoreTest :
    FunSpec({
        val member = PopupViewer.member("abc")
        val memberKey = "popup:view:100:m:abc"

        fun store(ops: ValueOperations<String, String>) =
            PopupViewMarkStore(mockk<StringRedisTemplate> { every { opsForValue() } returns ops })

        test("SET NX EX 한 번으로 key · 값 \"1\" · TTL 10분을 함께 넘긴다") {
            val ops = mockk<ValueOperations<String, String>>()
            every { ops.setIfAbsent(memberKey, "1", Duration.ofMinutes(10)) } returns true

            store(ops).markIfAbsent(100L, member) shouldBe true

            verify(exactly = 1) { ops.setIfAbsent(memberKey, "1", Duration.ofMinutes(10)) }
            // 확인 후 저장(두 단계)을 하지 않는다
            verify(exactly = 0) { ops.set(any(), any()) }
            verify(exactly = 0) { ops.set(any(), any(), any<Duration>()) }
        }

        test("비회원 key 는 popup:view:{popupId}:a:{sha256}") {
            val viewer = PopupViewer.anonymous("203.0.113.7", null, "172.18.0.5", "ua")
            val ops = mockk<ValueOperations<String, String>>()
            every { ops.setIfAbsent("popup:view:7:${viewer.key}", "1", Duration.ofMinutes(10)) } returns true

            store(ops).markIfAbsent(7L, viewer) shouldBe true
        }

        test("이미 메모가 있으면 false") {
            val ops = mockk<ValueOperations<String, String>>()
            every { ops.setIfAbsent(memberKey, "1", any<Duration>()) } returns false

            store(ops).markIfAbsent(100L, member) shouldBe false
        }

        test("Redis 가 NULL 을 돌려주면 false") {
            val ops = mockk<ValueOperations<String, String>>()
            every { ops.setIfAbsent(memberKey, "1", any<Duration>()) } returns null

            store(ops).markIfAbsent(100L, member) shouldBe false
        }

        test("Redis 오류면 예외를 전파하지 않고 false 와 WARN") {
            val ops = mockk<ValueOperations<String, String>>()
            every { ops.setIfAbsent(memberKey, "1", any<Duration>()) } throws RedisConnectionFailureException("down")

            LogCapture(PopupViewMarkStore::class.java.name).use { capture ->
                store(ops).markIfAbsent(100L, member) shouldBe false

                capture.messages() shouldHaveSize 1
                capture.messages().first() shouldContain "조회 메모 기록 실패 popupId=100"
            }
        }
    })
