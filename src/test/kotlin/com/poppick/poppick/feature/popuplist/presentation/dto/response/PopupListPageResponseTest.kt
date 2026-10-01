package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
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
            val cursor = PopupSearchCursor(LocalDate.of(2026, 9, 20), 1715)

            val encoded = PopupListPageResponse.encodeCursor(cursor)

            encoded shouldBe "MjAyNi0wOS0yMDoxNzE1"
            encoded shouldMatch Regex("[A-Za-z0-9_-]+")
            PopupListPageResponse.decodeCursor(encoded) shouldBe cursor
        }

        test("오픈일 없는 구간의 cursor 도 인코딩 후 디코딩하면 startDate 가 null 로 복원된다") {
            val cursor = PopupSearchCursor(null, 1703)

            val encoded = PopupListPageResponse.encodeCursor(cursor)

            encoded shouldBe "XzoxNzAz"
            PopupListPageResponse.decodeCursor(encoded) shouldBe cursor
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
                shouldThrow<AppException> { PopupListPageResponse.decodeCursor(cursor) }
                    .errorType shouldBe ErrorType.INVALID_PAGING_PARAMETER
            }
        }

        test("hasNext 가 true 이면 nextCursor 는 페이지 마지막 팝업의 (startDate, popupId) 이다") {
            val last = popup(1715, LocalDate.of(2026, 9, 20))

            val response = PopupListPageResponse.from(slice(listOf(popup(1720, LocalDate.of(2026, 9, 25)), last), hasNext = true))

            response.content.map { it.popupId } shouldBe listOf(1720L, 1715L)
            response.hasNext shouldBe true
            PopupListPageResponse.decodeCursor(response.nextCursor!!) shouldBe PopupSearchCursor(last.startDate, 1715)
        }

        test("마지막 팝업의 오픈일이 없으면 nextCursor 는 오픈일 없는 구간 cursor 다") {
            val response = PopupListPageResponse.from(slice(listOf(popup(1703, null)), hasNext = true))

            PopupListPageResponse.decodeCursor(response.nextCursor!!) shouldBe PopupSearchCursor(null, 1703)
        }

        test("hasNext 가 false 이거나 결과가 없으면 nextCursor 는 null 이다") {
            PopupListPageResponse.from(slice(listOf(popup(1715, null)), hasNext = false)).nextCursor shouldBe null
            PopupListPageResponse.from(slice(emptyList(), hasNext = false)).nextCursor shouldBe null
        }
    })
