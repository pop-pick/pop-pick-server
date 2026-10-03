package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter.UpsertResult
import com.poppick.poppick.global.exception.AppException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional

class PopupWriterTest :
    FunSpec({
        val now = OffsetDateTime.of(2026, 10, 3, 5, 30, 0, 0, ZoneOffset.ofHours(9))
        val place =
            KakaoPlace(
                id = "1001",
                placeName = "성수 캐릭터 팝업(이전)",
                addressJibun = "서울 성동구 성수동2가 1",
                addressRoad = "서울 성동구 연무장길 12",
                latitude = 37.55,
                longitude = 127.06,
                placeUrl = "http://place.map.kakao.com/1001",
                raw = mapOf("id" to "1001", "category_name" to "가정,생활 > 팝업스토어"),
            )

        test("신규 장소는 카카오 값으로 새 행을 만들고 lastSeenAt 을 기록한다") {
            val repository = mockk<PopupRepository>()
            val saved = slot<PopupEntity>()
            every { repository.findBySourceAndExternalId(SourceType.KAKAO_MAP, "1001") } returns null
            every { repository.save(capture(saved)) } answers { firstArg() }

            PopupWriter(repository).upsertFromKakao(place, now) shouldBe UpsertResult.CREATED

            with(saved.captured) {
                source shouldBe SourceType.KAKAO_MAP
                externalId shouldBe "1001"
                placeId shouldBe "1001"
                placeResolution shouldBe PlaceResolution.EXACT
                title shouldBe place.placeName
                sourceUrls shouldBe listOf("http://place.map.kakao.com/1001")
                enrichedAt shouldBe null
                lastSeenAt shouldBe now
            }
        }

        test("기존 행은 장소 필드 · rawPayload · sourceUrls · lastSeenAt 만 갱신하고 보강 결과는 건드리지 않는다") {
            val repository = mockk<PopupRepository>()
            val existing =
                PopupEntity(
                    id = 7,
                    source = SourceType.KAKAO_MAP,
                    externalId = "1001",
                    placeId = "1001",
                    title = "망그러진 곰 팝업스토어",
                    placeName = "성수 캐릭터 팝업",
                    addressRoad = "서울 성동구 연무장길 10",
                    latitude = 37.54,
                    longitude = 127.05,
                    sourceUrls = listOf("https://www.instagram.com/p/abc"),
                    rawPayload = mapOf("id" to "1001"),
                    startDate = LocalDate.of(2026, 9, 10),
                    endDate = LocalDate.of(2026, 9, 1),
                    interestCategoryId = 1,
                    enrichRetryCount = 1,
                    lastSeenAt = now.minusDays(1),
                )
            every { repository.findBySourceAndExternalId(SourceType.KAKAO_MAP, "1001") } returns existing

            PopupWriter(repository).upsertFromKakao(place, now) shouldBe UpsertResult.UPDATED

            verify(exactly = 0) { repository.save(any()) }
            with(existing) {
                placeName shouldBe place.placeName
                addressRoad shouldBe place.addressRoad
                addressJibun shouldBe place.addressJibun
                latitude shouldBe place.latitude
                longitude shouldBe place.longitude
                rawPayload shouldBe place.raw
                sourceUrls shouldBe listOf("https://www.instagram.com/p/abc", "http://place.map.kakao.com/1001")
                title shouldBe "망그러진 곰 팝업스토어"
                startDate shouldBe LocalDate.of(2026, 9, 10)
                interestCategoryId shouldBe 1
                enrichRetryCount shouldBe 1
                lastSeenAt shouldBe now
            }
        }

        test("updateImages 는 imageUrls · imageCheckedAt 두 필드만 바꾼다") {
            val repository = mockk<PopupRepository>()
            val existing =
                PopupEntity(
                    id = 7,
                    source = SourceType.KAKAO_MAP,
                    externalId = "1001",
                    title = "망그러진 곰 팝업스토어",
                    brand = "망그러진 곰",
                    endDate = LocalDate.of(2026, 10, 20),
                    enrichedAt = now.minusDays(1),
                    lastSeenAt = now.minusDays(1),
                )
            every { repository.findById(7) } returns Optional.of(existing)

            PopupWriter(repository).updateImages(7, listOf("https://img.example.com/1.jpg"), now)

            verify(exactly = 0) { repository.save(any()) }
            with(existing) {
                imageUrls shouldBe listOf("https://img.example.com/1.jpg")
                imageCheckedAt shouldBe now
                title shouldBe "망그러진 곰 팝업스토어"
                brand shouldBe "망그러진 곰"
                endDate shouldBe LocalDate.of(2026, 10, 20)
                enrichedAt shouldBe now.minusDays(1)
                lastSeenAt shouldBe now.minusDays(1)
            }
        }

        test("updateImages 에 빈 목록이 오면 imageUrls 는 그대로 두고 imageCheckedAt 만 갱신한다") {
            val repository = mockk<PopupRepository>()
            val existing = PopupEntity(id = 7, source = SourceType.KAKAO_MAP, title = "팝업", imageUrls = null)
            every { repository.findById(7) } returns Optional.of(existing)

            PopupWriter(repository).updateImages(7, emptyList(), now)

            existing.imageUrls shouldBe null
            existing.imageCheckedAt shouldBe now
        }

        test("updateImages 대상이 없으면 예외") {
            val repository = mockk<PopupRepository>()
            every { repository.findById(7) } returns Optional.empty()

            shouldThrow<AppException> { PopupWriter(repository).updateImages(7, emptyList(), now) }
        }
    })
