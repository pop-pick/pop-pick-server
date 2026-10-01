package com.poppick.poppick.feature.popuplist.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.Slice
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Base64

private const val DELIMITER = ":"
private const val NULL_START_DATE = "_"
private const val POPULAR_PREFIX = "P"

data class PopupListPageResponse(
    @field:Schema(description = "팝업 목록")
    val content: List<PopupListResponse>,
    @field:Schema(description = "다음 페이지 존재 여부", example = "true")
    val hasNext: Boolean,
    @field:Schema(
        description =
            "다음 페이지 요청의 cursor로 그대로 보내는 값입니다. hasNext가 false면 null입니다. " +
                "같은 keyword · sort · areaId 요청에서만 사용할 수 있습니다.",
        example = "MjAyNi0wOS0yMDoxNzE1",
        nullable = true,
    )
    val nextCursor: String?,
) {
    companion object {
        fun from(
            slice: Slice<Popup>,
            sort: PopupSortType,
            categoryNames: Map<Int, String>,
            areaNames: Map<Int, String>,
        ) = PopupListPageResponse(
            content = slice.content.map { PopupListResponse.from(it, categoryNames, areaNames) },
            hasNext = slice.hasNext,
            nextCursor =
                slice.content
                    .lastOrNull()
                    ?.takeIf { slice.hasNext }
                    ?.let { encodeCursor(PopupSearchCursor.of(it, sort)) },
        )

        /**
         * Base64URL(패딩 없음)로 인코딩한다.
         * - 최신순: "{yyyy-MM-dd 또는 _}:{popupId}" (기존 형식 그대로)
         * - 인기순: "P:{viewCount}:{popupId}"
         */
        fun encodeCursor(cursor: PopupSearchCursor): String {
            val raw =
                when (cursor) {
                    is PopupSearchCursor.Latest -> "${cursor.startDate ?: NULL_START_DATE}$DELIMITER${cursor.popupId}"
                    is PopupSearchCursor.Popular -> "$POPULAR_PREFIX$DELIMITER${cursor.viewCount}$DELIMITER${cursor.popupId}"
                }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray(Charsets.UTF_8))
        }

        /**
         * encodeCursor 의 역변환. 형식이 맞지 않거나 sort 와 다른 정렬의 cursor 면 INVALID_PAGING_PARAMETER.
         * 최신순 cursor 는 조각 2개, 인기순 cursor 는 "P" 로 시작하는 조각 3개라 서로 섞이지 않는다.
         */
        fun decodeCursor(
            cursor: String,
            sort: PopupSortType,
        ): PopupSearchCursor {
            val raw =
                try {
                    String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)
                } catch (e: IllegalArgumentException) {
                    throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
                }
            val parts = raw.split(DELIMITER)

            return when (sort) {
                PopupSortType.LATEST -> decodeLatest(parts)
                PopupSortType.POPULAR -> decodePopular(parts)
            }
        }

        private fun decodePopular(parts: List<String>): PopupSearchCursor.Popular {
            if (parts.size != 3 || parts[0] != POPULAR_PREFIX) throw AppException(ErrorType.INVALID_PAGING_PARAMETER)

            val viewCount =
                parts[1].toLongOrNull()?.takeIf { it >= 0 }
                    ?: throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
            return PopupSearchCursor.Popular(viewCount, parsePopupId(parts[2]))
        }

        private fun decodeLatest(parts: List<String>): PopupSearchCursor.Latest {
            if (parts.size != 2) throw AppException(ErrorType.INVALID_PAGING_PARAMETER)

            val startDate =
                parts[0].takeUnless { it == NULL_START_DATE }?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (e: DateTimeParseException) {
                        throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
                    }
                }
            return PopupSearchCursor.Latest(startDate, parsePopupId(parts[1]))
        }

        private fun parsePopupId(value: String): Long =
            value.toLongOrNull()?.takeIf { it > 0 }
                ?: throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
    }
}
