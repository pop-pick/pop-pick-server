package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.LogCapture
import com.poppick.poppick.feature.popup.domain.PerplexityEnrichResult
import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupEnrichment
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

class PopupEnrichmentMergerTest :
    FunSpec({
        val merger = PopupEnrichmentMerger()
        val now = OffsetDateTime.of(2026, 9, 25, 5, 30, 0, 0, ZoneOffset.ofHours(9))
        val categories =
            listOf("캐릭터/IP", "패션/브랜드", "F&B", "전시/아트", "뷰티", "게임/엔터", "라이프스타일", "기타")
                .mapIndexed { index, name -> name to index + 1 }
                .toMap()
        val areas = Fixtures.favoriteAreas.associate { it.area to it.id }

        val popup =
            Popup(
                id = 1,
                source = SourceType.KAKAO_MAP,
                externalId = "1001",
                placeId = "1001",
                placeResolution = PlaceResolution.EXACT,
                title = "성수 캐릭터 팝업",
                placeName = "성수 캐릭터 팝업",
                addressRoad = "서울 성동구 연무장길 10",
                addressJibun = "서울 성동구 성수동2가 322-1",
                latitude = 37.5432,
                longitude = 127.0567,
                sourceUrls = listOf("http://place.map.kakao.com/1001"),
                rawPayload = mapOf("category_name" to "가정,생활 > 팝업스토어"),
            )

        val found =
            PopupEnrichment(
                found = true,
                matchesPlace = true,
                title = "망그러진 곰 팝업스토어",
                brand = "망그러진 곰",
                interestCategory = "캐릭터/IP",
                area = "성수",
                description = "첫 오프라인 팝업.",
                tags = listOf("캐릭터", "굿즈"),
                startDate = "2026-09-10",
                endDate = "2026-10-12",
                openingHours = "매일 11:00~20:00",
                reservationType = ReservationType.RESERVATION,
                reservationUrl = "https://booking.naver.com/booking/6/bizes/123",
                reservationOpenAt = "2026-09-01T10:00:00+09:00",
                entryFee = 5000,
            )
        val empty = PopupEnrichment(found = true, matchesPlace = true)
        // 스키마상 interest_category 는 found=false 여도 항상 온다.
        val notFound = PopupEnrichment(found = false, matchesPlace = true, interestCategory = "기타")
        val searchUrls = listOf("https://www.instagram.com/p/abc", "https://booking.naver.com/booking/6/bizes/123")

        fun merge(
            enrichment: PopupEnrichment,
            base: Popup = popup,
            urls: List<String> = searchUrls,
        ) = merger.merge(base, PerplexityEnrichResult(enrichment, urls), categories, areas, now)

        // 핵심 필드가 모두 채워진 팝업
        val complete = merge(found)

        context("정보를 못 찾음") {
            test("found=false → retry+1 · enrichedAt 만 바뀌고 다른 필드는 그대로") {
                merge(notFound) shouldBe popup.copy(interestCategoryId = 8, enrichRetryCount = 1, enrichedAt = now)
            }

            test("이미 채워진 팝업에 빈 found=false 가 와도 값은 유지되고, 핵심 필드가 차 있어 retry 는 그대로") {
                merge(notFound.copy(interestCategory = null), base = complete) shouldBe complete
            }

            test("found=false 의 기타도 새 값이라 기존 카테고리를 덮는다(새 값 ?: 기존 값)") {
                merge(notFound, base = complete) shouldBe complete.copy(interestCategoryId = 8)
            }

            test("found=false 여도 값이 있는 필드는 반영하고 나머지는 기존 값 유지, 출처 URL 은 붙이지 않는다") {
                val partial =
                    notFound.copy(interestCategory = null, brand = "새 브랜드", description = "부분 소개.", startDate = "2026-09-20")

                merge(partial, base = complete) shouldBe
                    complete.copy(brand = "새 브랜드", description = "부분 소개.", startDate = LocalDate.of(2026, 9, 20))
                merge(partial) shouldBe
                    popup.copy(
                        brand = "새 브랜드",
                        description = "부분 소개.",
                        startDate = LocalDate.of(2026, 9, 20),
                        interestCategoryId = 8,
                        enrichRetryCount = 1,
                        enrichedAt = now,
                    )
            }

            test("start_date 만 있으면 시작일만, end_date 만 있으면 종료일만 반영한다") {
                val startOnly = merge(notFound.copy(startDate = "2026-09-20"))
                startOnly.startDate shouldBe LocalDate.of(2026, 9, 20)
                startOnly.endDate.shouldBeNull()

                val endOnly = merge(empty.copy(endDate = "2026-10-30"))
                endOnly.startDate.shouldBeNull()
                endOnly.endDate shouldBe LocalDate.of(2026, 10, 30)
                endOnly.enrichRetryCount shouldBe 1
            }

            test("matches_place=false 는 오귀속이라 카테고리 · area 외의 응답 값을 버린다(found=true 여도)") {
                merge(found.copy(matchesPlace = false, title = "다른 브랜드 팝업", area = null)) shouldBe
                    popup.copy(interestCategoryId = 1, enrichRetryCount = 1, enrichedAt = now)
            }

            test("found=false 여도 area · 카테고리는 반영한다") {
                merge(notFound.copy(area = "홍대", interestCategory = "뷰티")) shouldBe
                    popup.copy(areaId = 3, interestCategoryId = 5, enrichRetryCount = 1, enrichedAt = now)
            }

            test("matches_place=false 여도 카테고리 · area 는 반영한다. 나머지 필드는 그대로, retry+1") {
                merge(found.copy(matchesPlace = false, title = "다른 브랜드 팝업", area = "용산", interestCategory = "F&B")) shouldBe
                    popup.copy(areaId = 5, interestCategoryId = 3, enrichRetryCount = 1, enrichedAt = now)
                merge(found.copy(matchesPlace = false, title = "다른 브랜드 팝업", interestCategory = "F&B"), base = complete) shouldBe
                    complete.copy(interestCategoryId = 3, enrichRetryCount = 1)
            }
        }

        context("retry 카운터") {
            test("핵심 필드가 모두 채워지면 retry 는 그대로") {
                complete.hasCoreFields() shouldBe true
                complete.enrichRetryCount shouldBe 0
                complete.enrichedAt shouldBe now
            }

            test("종료일이 비면 retry+1") {
                merge(found.copy(endDate = null)).enrichRetryCount shouldBe 1
            }

            test("시작일이 비면 retry+1") {
                merge(found.copy(startDate = null)).enrichRetryCount shouldBe 1
            }

            test("카테고리를 매핑하지 못해도 기타로 채워져 기간이 있으면 retry 는 그대로") {
                val merged = merge(found.copy(interestCategory = "없는 카테고리"))

                merged.interestCategoryId shouldBe 8
                merged.enrichRetryCount shouldBe 0
            }

            test("재보강으로 핵심 필드가 채워지면 retry 는 그대로") {
                val partial = merge(found.copy(endDate = null))

                merge(found, base = partial).enrichRetryCount shouldBe 1
            }

            test("종료일이 오늘 이전이어도 그대로 저장한다(종료 여부는 조회 시 계산)") {
                val merged = merge(found.copy(startDate = "2026-08-01", endDate = "2026-09-24"))

                merged.endDate shouldBe LocalDate.of(2026, 9, 24)
                merged.enrichRetryCount shouldBe 0
            }
        }

        context("필드 병합") {
            test("응답 필드를 채운다") {
                complete.title shouldBe "망그러진 곰 팝업스토어"
                complete.brand shouldBe "망그러진 곰"
                complete.interestCategoryId shouldBe 1
                complete.startDate shouldBe LocalDate.of(2026, 9, 10)
                complete.endDate shouldBe LocalDate.of(2026, 10, 12)
                complete.reservationType shouldBe ReservationType.RESERVATION
                complete.reservationOpenAt shouldBe OffsetDateTime.of(2026, 9, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9))
                complete.entryFee shouldBe 5000
                complete.sourceUrls shouldBe listOf("http://place.map.kakao.com/1001") + searchUrls
            }

            test("장소 필드 · source · externalId · rawPayload 는 덮지 않는다") {
                complete.source shouldBe popup.source
                complete.externalId shouldBe popup.externalId
                complete.rawPayload shouldBe popup.rawPayload
                complete.placeId shouldBe popup.placeId
                complete.placeName shouldBe popup.placeName
                complete.addressRoad shouldBe popup.addressRoad
                complete.addressJibun shouldBe popup.addressJibun
                complete.latitude shouldBe popup.latitude
                complete.longitude shouldBe popup.longitude
                complete.placeResolution shouldBe popup.placeResolution
            }
        }

        context("null 덮어쓰기 금지") {
            test("응답이 전부 비어 있으면 기존 값을 모두 유지하고 retry 도 그대로") {
                merge(empty, base = complete, urls = emptyList()) shouldBe complete
            }

            test("reservation_type 이 UNKNOWN 이면 기존 값 유지, 아니면 새 값") {
                merge(empty.copy(reservationType = ReservationType.UNKNOWN), base = complete).reservationType shouldBe
                    ReservationType.RESERVATION
                merge(empty.copy(reservationType = ReservationType.WAITING), base = complete).reservationType shouldBe
                    ReservationType.WAITING
            }

            test("새 값이 있으면 새 값을 쓴다") {
                val merged = merge(empty.copy(endDate = "2026-11-30", entryFee = 0, title = "새 제목"), base = complete)

                merged.endDate shouldBe LocalDate.of(2026, 11, 30)
                merged.startDate shouldBe LocalDate.of(2026, 9, 10)
                merged.entryFee shouldBe 0
                merged.title shouldBe "새 제목"
            }

            test("새 시작일과 기존 종료일이 역전되면 새 응답 값만 쓴다") {
                val merged = merge(empty.copy(startDate = "2026-11-01"), base = complete)

                merged.startDate shouldBe LocalDate.of(2026, 11, 1)
                merged.endDate.shouldBeNull()
                merged.enrichRetryCount shouldBe 1
            }
        }

        context("12-31 플레이스홀더") {
            test("12-31 이고 시작일과 90일 넘게 떨어지면 종료일을 NULL 로 두고 retry+1") {
                val merged = merge(found.copy(startDate = "2026-06-01", endDate = "2026-12-31"))

                merged.endDate.shouldBeNull()
                merged.startDate shouldBe LocalDate.of(2026, 6, 1)
                merged.enrichRetryCount shouldBe 1
            }

            test("12-31 이어도 기간이 90일 이내면 유지") {
                merge(found.copy(startDate = "2026-12-01", endDate = "2026-12-31")).endDate shouldBe LocalDate.of(2026, 12, 31)
            }

            test("12-31 이 아니면 기간이 길어도 유지") {
                merge(found.copy(startDate = "2026-06-01", endDate = "2026-12-30")).endDate shouldBe LocalDate.of(2026, 12, 30)
            }

            test("새 응답의 12-31 을 버린 뒤 기존 종료일이 있으면 그 값을 쓴다") {
                val merged = merge(found.copy(startDate = null, endDate = "2026-12-31"), base = complete)

                merged.endDate shouldBe LocalDate.of(2026, 10, 12)
                merged.enrichRetryCount shouldBe 0
            }

            test("이전 실행에서 저장된 12-31 도 재보강에서 걸러진다") {
                val stored = complete.copy(startDate = LocalDate.of(2026, 6, 1), endDate = LocalDate.of(2026, 12, 31))

                merge(empty, base = stored).endDate.shouldBeNull()
            }
        }

        context("장기 기간") {
            fun warnings(enrichment: PopupEnrichment) =
                LogCapture(PopupEnrichmentMerger::class.java.name).use { capture ->
                    merge(enrichment) to capture.messages().filter { "장기 기간 의심" in it }
                }

            test("180일을 넘으면 WARN 만 남기고 값은 유지한다") {
                val (merged, logs) = warnings(found.copy(startDate = "2026-05-09", endDate = "2026-11-25"))

                merged.startDate shouldBe LocalDate.of(2026, 5, 9)
                merged.endDate shouldBe LocalDate.of(2026, 11, 25)
                logs shouldBe listOf("enrich: 장기 기간 의심 popupId=1 start=2026-05-09 end=2026-11-25")
            }

            test("100일이면 로그 없음") {
                val (merged, logs) = warnings(found.copy(startDate = "2026-09-01", endDate = "2026-12-10"))

                merged.endDate shouldBe LocalDate.of(2026, 12, 10)
                logs.shouldBeEmpty()
            }

            test("12-31 로 버려진 종료일은 장기 기간 검사 대상이 아니다") {
                val (merged, logs) = warnings(found.copy(startDate = "2026-03-01", endDate = "2026-12-31"))

                merged.endDate.shouldBeNull()
                logs.shouldBeEmpty()
            }
        }

        context("값 정규화") {
            test("title 이 비어 있으면 기존 제목을 유지한다") {
                merge(found.copy(title = " ")).title shouldBe popup.title
            }

            test("시작일 > 종료일이면 둘 다 NULL") {
                val merged = merge(found.copy(startDate = "2026-10-12", endDate = "2026-09-30"))

                merged.startDate.shouldBeNull()
                merged.endDate.shouldBeNull()
            }

            test("날짜 · 시각 형식이 틀리면 NULL, 음수 입장료는 NULL") {
                val merged = merge(found.copy(startDate = "9월 10일", reservationOpenAt = "내일 오전", entryFee = -1))

                merged.startDate.shouldBeNull()
                merged.reservationOpenAt.shouldBeNull()
                merged.entryFee.shouldBeNull()
            }

            test("description · title 이 길어도(300자) 자르지 않고 그대로 전달한다") {
                val longDescription = "가".repeat(300)
                val longTitle = "나".repeat(300)

                val merged = merge(found.copy(description = longDescription, title = longTitle))

                merged.description shouldBe longDescription
                merged.title shouldBe longTitle
            }
        }

        context("opening_hours") {
            test("응답의 한 줄 운영시간을 그대로 저장한다") {
                complete.openingHours shouldBe "매일 11:00~20:00"
            }

            test("공백을 다듬어 저장한다") {
                merge(found.copy(openingHours = "  매일 11:00~20:00, 월 휴무 ")).openingHours shouldBe "매일 11:00~20:00, 월 휴무"
            }

            test("재보강 응답이 null · 빈 문자열이면 기존 값을 유지한다") {
                merge(empty.copy(openingHours = null), base = complete).openingHours shouldBe "매일 11:00~20:00"
                merge(empty.copy(openingHours = " "), base = complete).openingHours shouldBe "매일 11:00~20:00"
            }

            test("새 값이 있으면 새 값을 쓴다") {
                merge(empty.copy(openingHours = "화~일 10:30~22:00, 월 휴무"), base = complete).openingHours shouldBe "화~일 10:30~22:00, 월 휴무"
            }
        }

        context("카테고리(interest_category)") {
            fun categoryWarnings(
                enrichment: PopupEnrichment,
                base: Popup = popup,
            ) = LogCapture(PopupEnrichmentMerger::class.java.name).use { capture ->
                merge(enrichment, base = base) to capture.messages().filter { "카테고리 매핑 실패" in it }
            }

            test("found=false 여도 카테고리를 반영한다") {
                merge(notFound.copy(interestCategory = "전시/아트")).interestCategoryId shouldBe 4
                merge(notFound.copy(interestCategory = "전시/아트"), base = complete).interestCategoryId shouldBe 4
            }

            test("응답이 null 이고 기존 값도 없으면 기타로 저장하고 WARN") {
                val (merged, logs) = categoryWarnings(notFound.copy(interestCategory = null))

                merged.interestCategoryId shouldBe 8
                logs shouldBe listOf("enrich: 카테고리 매핑 실패, 기타 로 저장 popupId=1 interestCategory=null")
            }

            test("매핑 불가 값이고 기존 값도 없으면 기타로 저장하고 WARN") {
                val (merged, logs) = categoryWarnings(found.copy(matchesPlace = false, interestCategory = "판교"))

                merged.interestCategoryId shouldBe 8
                logs shouldBe listOf("enrich: 카테고리 매핑 실패, 기타 로 저장 popupId=1 interestCategory=판교")
            }

            test("응답이 null · 매핑 불가여도 기존 값이 있으면 유지하고 WARN 은 없다") {
                val (nullMerged, nullLogs) = categoryWarnings(notFound.copy(interestCategory = null), base = complete)
                val (unknownMerged, unknownLogs) = categoryWarnings(found.copy(interestCategory = "판교"), base = complete)

                nullMerged.interestCategoryId shouldBe 1
                unknownMerged.interestCategoryId shouldBe 1
                (nullLogs + unknownLogs).shouldBeEmpty()
            }

            test("기타 id 는 이름으로 찾는다") {
                val shuffled = categories + ("기타" to 99)

                merger
                    .merge(popup, PerplexityEnrichResult(notFound.copy(interestCategory = null), searchUrls), shuffled, areas, now)
                    .interestCategoryId shouldBe 99
            }
        }

        context("상권(area)") {
            fun areaWarnings(
                enrichment: PopupEnrichment,
                base: Popup = popup,
            ) = LogCapture(PopupEnrichmentMerger::class.java.name).use { capture ->
                merge(enrichment, base = base) to capture.messages().filter { "알 수 없는 상권" in it }
            }

            test("상권 이름을 favorite_area id 로 매핑한다") {
                complete.areaId shouldBe 1
                merge(found.copy(area = "용산")).areaId shouldBe 5
            }

            test("강남은 7 로 매핑된다") {
                merge(found.copy(area = "강남")).areaId shouldBe 7
            }

            test("응답이 null 이면 기존 areaId 를 유지한다") {
                merge(found.copy(area = null), base = complete).areaId shouldBe 1
                merge(found.copy(area = null)).areaId.shouldBeNull()
            }

            test("알 수 없는 이름이면 기존 값을 유지하고 WARN 을 남긴다") {
                val (merged, logs) = areaWarnings(found.copy(area = "판교"), base = complete)

                merged.areaId shouldBe 1
                logs shouldBe listOf("enrich: 알 수 없는 상권 popupId=1 address=서울 성동구 연무장길 10 area=판교")
            }

            test("응답 area 가 비면 기존 값을 유지하고 WARN 을 남긴다") {
                val (merged, logs) = areaWarnings(found.copy(area = " "), base = complete)

                merged.areaId shouldBe 1
                logs shouldBe listOf("enrich: 알 수 없는 상권 popupId=1 address=서울 성동구 연무장길 10 area= ")
            }

            test("found=false 여도 새 area 가 있으면 기존 값을 덮는다") {
                merge(notFound.copy(area = "홍대"), base = complete).areaId shouldBe 3
            }

            test("found=false 에 매핑 불가 area 면 기존 값 유지") {
                val (merged, logs) = areaWarnings(notFound.copy(area = "판교", interestCategory = null), base = complete)

                merged shouldBe complete
                logs shouldBe listOf("enrich: 알 수 없는 상권 popupId=1 address=서울 성동구 연무장길 10 area=판교")
            }

            test("areaId 가 비어도 핵심 필드가 차 있으면 retry 는 그대로") {
                val merged = merge(found.copy(area = null))

                merged.areaId.shouldBeNull()
                merged.enrichRetryCount shouldBe 0
            }
        }

        context("reservation_url 검증") {
            test("검색 결과에 정확히 있으면 채택") {
                val url = "https://booking.naver.com/booking/6/bizes/123"
                merge(found.copy(reservationUrl = url)).reservationUrl shouldBe url
            }

            test("검색 결과에 같은 host 가 있으면 채택") {
                val url = "https://booking.naver.com/booking/6/bizes/999"
                merge(found.copy(reservationUrl = url)).reservationUrl shouldBe url
            }

            test("검색 결과에 없는 host 면 NULL") {
                merge(found.copy(reservationUrl = "https://made-up-popup.kr/reserve")).reservationUrl.shouldBeNull()
            }
        }
    })
