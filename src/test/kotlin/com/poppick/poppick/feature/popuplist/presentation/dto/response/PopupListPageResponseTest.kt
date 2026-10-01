package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.time.LocalDate
import java.util.Base64

class PopupListPageResponseTest :
    FunSpec({
        fun popup(
            id: Long,
            startDate: LocalDate?,
        ) = Popup(id = id, source = SourceType.KAKAO_MAP, title = "팝업 $id", startDate = startDate)

        fun slice(
            content: List<Popup>,
            hasNext: Boolean,
        ) = Slice(content, Cursorable<PopupSearchCursor>(null, 10), hasNext)

        fun encodeRaw(raw: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())

        test("오픈일 있는 cursor 는 인코딩 후 디코딩하면 같은 값이고, URL 에 그대로 넣을 수 있는 문자만 쓴다") {
            val cursor = PopupSearchCursor.Latest(LocalDate.of(2026, 9, 20), 1715)

            val encoded = PopupListPageResponse.encodeCursor(cursor)

            encoded shouldBe "MjAyNi0wOS0yMDoxNzE1"
            encoded shouldMatch Regex("[A-Za-z0-9_-]+")
            PopupListPageResponse.decodeCursor(encoded, PopupSortType.LATEST) shouldBe cursor
        }

        test("오픈일 없는 구간의 cursor 도 인코딩 후 디코딩하면 startDate 가 null 로 복원된다") {
            val cursor = PopupSearchCursor.Latest(null, 1703)

            val encoded = PopupListPageResponse.encodeCursor(cursor)

            encoded shouldBe "XzoxNzAz"
            PopupListPageResponse.decodeCursor(encoded, PopupSortType.LATEST) shouldBe cursor
        }

        test("형식이 잘못된 cursor 는 INVALID_PAGING_PARAMETER 예외가 발생한다") {
            listOf(
                "1715", // 이전 방식의 popupId 를 그대로 보낸 경우
                "not base64!",
                "null",
                encodeRaw("2026-09-20"), // popupId 누락
                encodeRaw("2026-09-20:1715:1"), // 구분자 초과
                encodeRaw("2026-13-40:1715"), // 잘못된 날짜
                encodeRaw("2026-09-20:abc"), // 숫자가 아닌 popupId
                encodeRaw("2026-09-20:0"), // 양수가 아닌 popupId
                encodeRaw(":1715"), // 오픈일 자리가 비어 있음
            ).forEach { cursor ->
                shouldThrow<AppException> { PopupListPageResponse.decodeCursor(cursor, PopupSortType.LATEST) }
                    .errorType shouldBe ErrorType.INVALID_PAGING_PARAMETER
            }
        }

        test("hasNext 가 true 이면 nextCursor 는 페이지 마지막 팝업의 (startDate, popupId) 이다") {
            val last = popup(1715, LocalDate.of(2026, 9, 20))

            val response =
                PopupListPageResponse.from(
                    slice(listOf(popup(1720, LocalDate.of(2026, 9, 25)), last), hasNext = true),
                    PopupSortType.LATEST,
                    emptyMap(),
                    emptyMap(),
                )

            response.content.map { it.popupId } shouldBe listOf(1720L, 1715L)
            response.hasNext shouldBe true
            PopupListPageResponse.decodeCursor(response.nextCursor!!, PopupSortType.LATEST) shouldBe
                PopupSearchCursor.Latest(last.startDate, 1715)
        }

        test("마지막 팝업의 오픈일이 없으면 nextCursor 는 오픈일 없는 구간 cursor 다") {
            val response =
                PopupListPageResponse.from(
                    slice(listOf(popup(1703, null)), hasNext = true),
                    PopupSortType.LATEST,
                    emptyMap(),
                    emptyMap(),
                )

            PopupListPageResponse.decodeCursor(response.nextCursor!!, PopupSortType.LATEST) shouldBe PopupSearchCursor.Latest(null, 1703)
        }

        test("카드에 카테고리 이름을 담고, 카테고리가 없거나 알 수 없는 id 면 이름은 null 이다") {
            val names = mapOf(1 to "캐릭터/IP", 5 to "뷰티")
            val popups =
                listOf(
                    popup(3, null).copy(interestCategoryId = 1),
                    popup(2, null).copy(interestCategoryId = null),
                    popup(1, null).copy(interestCategoryId = 99),
                )

            val content = PopupListPageResponse.from(slice(popups, hasNext = false), PopupSortType.LATEST, names, emptyMap()).content

            content.map { it.interestCategoryId } shouldBe listOf(1, null, 99)
            content.map { it.interestCategoryName } shouldBe listOf("캐릭터/IP", null, null)
        }

        test("카드에 지역 id · 이름을 담고, 지역이 없거나 알 수 없는 id 면 이름은 null 이다(임의 값 없음)") {
            val popups =
                listOf(
                    popup(3, null).copy(areaId = 3),
                    popup(2, null).copy(areaId = null),
                    popup(1, null).copy(areaId = 99),
                )

            val content =
                PopupListPageResponse.from(slice(popups, hasNext = false), PopupSortType.LATEST, emptyMap(), mapOf(3 to "홍대")).content

            content.map { it.areaId } shouldBe listOf(3, null, 99)
            content.map { it.areaName } shouldBe listOf("홍대", null, null)
        }

        test("hasNext 가 false 이거나 결과가 없으면 nextCursor 는 null 이다") {
            PopupListPageResponse
                .from(
                    slice(listOf(popup(1715, null)), hasNext = false),
                    PopupSortType.LATEST,
                    emptyMap(),
                    emptyMap(),
                ).nextCursor shouldBe
                null
            PopupListPageResponse
                .from(
                    slice(emptyList(), hasNext = false),
                    PopupSortType.LATEST,
                    emptyMap(),
                    emptyMap(),
                ).nextCursor shouldBe
                null
        }

        context("인기순 cursor") {
            test("인코딩 후 디코딩하면 같은 값이고 원문은 P:{viewCount}:{popupId} 이다") {
                val cursor = PopupSearchCursor.Popular(viewCount = 42, popupId = 1715)

                val encoded = PopupListPageResponse.encodeCursor(cursor)

                String(Base64.getUrlDecoder().decode(encoded)) shouldBe "P:42:1715"
                encoded shouldMatch Regex("[A-Za-z0-9_-]+")
                PopupListPageResponse.decodeCursor(encoded, PopupSortType.POPULAR) shouldBe cursor
            }

            test("조회수 0 도 인코딩 · 디코딩된다") {
                val cursor = PopupSearchCursor.Popular(viewCount = 0, popupId = 1703)

                PopupListPageResponse.decodeCursor(PopupListPageResponse.encodeCursor(cursor), PopupSortType.POPULAR) shouldBe cursor
            }

            test("hasNext 가 true 이면 nextCursor 는 페이지 마지막 팝업의 (viewCount, popupId) 이다") {
                val first = popup(1720, null).copy(viewCount = 10)
                val last = popup(1715, LocalDate.of(2026, 9, 20)).copy(viewCount = 7)

                val response =
                    PopupListPageResponse.from(
                        slice(listOf(first, last), hasNext = true),
                        PopupSortType.POPULAR,
                        emptyMap(),
                        emptyMap(),
                    )

                response.content.map { it.popupId } shouldBe listOf(1720L, 1715L)
                PopupListPageResponse.decodeCursor(response.nextCursor!!, PopupSortType.POPULAR) shouldBe
                    PopupSearchCursor.Popular(7, 1715)
            }

            test("형식이 잘못된 인기순 cursor 는 INVALID_PAGING_PARAMETER") {
                listOf(
                    encodeRaw("P:42"), // popupId 누락
                    encodeRaw("P:42:1715:1"), // 구분자 초과
                    encodeRaw("X:42:1715"), // 접두어 다름
                    encodeRaw("P:-1:1715"), // 음수 조회수
                    encodeRaw("P:abc:1715"), // 숫자가 아닌 조회수
                    encodeRaw("P:42:0"), // 양수가 아닌 popupId
                    "not base64!",
                ).forEach { cursor ->
                    shouldThrow<AppException> { PopupListPageResponse.decodeCursor(cursor, PopupSortType.POPULAR) }
                        .errorType shouldBe ErrorType.INVALID_PAGING_PARAMETER
                }
            }
        }

        context("정렬과 cursor 종류가 다르면 400") {
            test("sort=popular 에 최신순 cursor(오픈일 있음 · 없음)를 보내면 INVALID_PAGING_PARAMETER") {
                listOf(
                    PopupSearchCursor.Latest(LocalDate.of(2026, 9, 20), 1715),
                    PopupSearchCursor.Latest(null, 1703),
                ).forEach { latest ->
                    shouldThrow<AppException> {
                        PopupListPageResponse.decodeCursor(PopupListPageResponse.encodeCursor(latest), PopupSortType.POPULAR)
                    }.errorType shouldBe ErrorType.INVALID_PAGING_PARAMETER
                }
            }

            test("sort=latest 에 인기순 cursor 를 보내면 INVALID_PAGING_PARAMETER") {
                val popular = PopupListPageResponse.encodeCursor(PopupSearchCursor.Popular(42, 1715))

                shouldThrow<AppException> { PopupListPageResponse.decodeCursor(popular, PopupSortType.LATEST) }
                    .errorType shouldBe ErrorType.INVALID_PAGING_PARAMETER
            }
        }
    })
