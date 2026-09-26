package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter.UpsertResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.LocalDate

class PopupWriterTest :
    FunSpec({
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

        test("신규 장소는 카카오 값으로 새 행을 만든다") {
            val repository = mockk<PopupRepository>()
            val saved = slot<PopupEntity>()
            every { repository.findBySourceAndExternalId(SourceType.KAKAO_MAP, "1001") } returns null
            every { repository.save(capture(saved)) } answers { firstArg() }

            PopupWriter(repository, mockk()).upsertFromKakao(place) shouldBe UpsertResult.CREATED

            with(saved.captured) {
                source shouldBe SourceType.KAKAO_MAP
                externalId shouldBe "1001"
                placeId shouldBe "1001"
                placeResolution shouldBe PlaceResolution.EXACT
                title shouldBe place.placeName
                sourceUrls shouldBe listOf("http://place.map.kakao.com/1001")
                enrichedAt shouldBe null
            }
        }

        test("기존 행은 장소 필드 · rawPayload · sourceUrls 만 갱신하고 보강 결과는 건드리지 않는다") {
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
                )
            every { repository.findBySourceAndExternalId(SourceType.KAKAO_MAP, "1001") } returns existing

            PopupWriter(repository, mockk()).upsertFromKakao(place) shouldBe UpsertResult.UPDATED

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
            }
        }

        test("deleteAll 은 청크마다 임베딩 → 팝업 순으로 지우고 건수를 합산한다") {
            val popupRepository = mockk<PopupRepository>()
            val embeddingRepository = mockk<PopupEmbeddingRepository>()
            val ids = (1L..1001L).toList()
            every { embeddingRepository.deleteByPopupIdIn(any()) } answers { firstArg<Collection<Long>>().size / 2 }
            every { popupRepository.deleteByIds(any()) } answers { firstArg<List<Long>>().size }

            val result = PopupWriter(popupRepository, embeddingRepository).deleteAll(ids)

            verifyOrder {
                embeddingRepository.deleteByPopupIdIn(ids.take(1000))
                popupRepository.deleteByIds(ids.take(1000))
                embeddingRepository.deleteByPopupIdIn(listOf(1001L))
                popupRepository.deleteByIds(listOf(1001L))
            }
            result shouldBe PopupWriter.DeleteResult(popups = 1001, embeddings = 500)
        }
    })
