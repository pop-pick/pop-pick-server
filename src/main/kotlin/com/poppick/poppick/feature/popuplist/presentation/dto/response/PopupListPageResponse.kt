package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popuplist.domain.PopupListItem
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Slice
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

private const val DELIMITER = ":"
private const val NULL_START_DATE = "_"

data class PopupListPageResponse(
    @field:Schema(description = "팝업 목록")
    val content: List<PopupListResponse>,
    @field:Schema(description = "다음 페이지 존재 여부. true 이면 nextCursor 가 함께 내려간다.", example = "true")
    val hasNext: Boolean,
    @field:Schema(
        description =
            "다음 페이지 요청의 cursor 파라미터에 그대로 넣는 값. hasNext 가 false 이면 null. " +
                "서버 내부 형식이므로 해석하거나 직접 만들지 않는다.",
        example = "MjAyNi0wOS0yMDoxNzE1",
        nullable = true,
    )
    val nextCursor: String?,
) {
    companion object {
        fun from(slice: Slice<PopupListItem>) =
            PopupListPageResponse(
                content = slice.content.map { PopupListResponse.from(it.popup, it.wished) },
                hasNext = slice.hasNext,
                nextCursor =
                    slice.content
                        .lastOrNull()
                        ?.takeIf { slice.hasNext }
                        ?.let { encodeCursor(PopupSearchCursor.of(it.popup)) },
            )

        /** "{yyyy-MM-dd 또는 _}:{popupId}" 를 Base64URL(패딩 없음)로 인코딩한다. */
        fun encodeCursor(cursor: PopupSearchCursor): String {
            val raw = "${cursor.startDate ?: NULL_START_DATE}$DELIMITER${cursor.popupId}"
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
        }

        /** encodeCursor 의 역변환. 형식이 맞지 않으면 INVALID_PAGING_PARAMETER. */
        fun decodeCursor(cursor: String): PopupSearchCursor {
            val raw =
                try {
                    String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
                } catch (e: IllegalArgumentException) {
                    throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
                }
            val parts = raw.split(DELIMITER)
            if (parts.size != 2) throw AppException(ErrorType.INVALID_PAGING_PARAMETER)

            val startDate =
                parts[0].takeUnless { it == NULL_START_DATE }?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (e: DateTimeParseException) {
                        throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
                    }
                }
            val popupId =
                parts[1].toLongOrNull()?.takeIf { it > 0 }
                    ?: throw AppException(ErrorType.INVALID_PAGING_PARAMETER)

            return PopupSearchCursor(startDate, popupId)
        }
    }
}
