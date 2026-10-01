package com.poppick.poppick.feature.wish.presentation.dto.response

import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.wish.domain.WishedPopup
import com.poppick.poppick.global.util.KST
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.OffsetDateTime

/** 내 찜 목록 항목. 팝업 목록 항목 + startDate · ended · wishedAt. */
data class WishResponse(
    @field:Schema(description = "팝업 id", example = "120")
    val popupId: Long,
    @field:Schema(description = "대표 이미지(첫 장). 없으면 NULL", example = "https://img.example/1.jpg", nullable = true)
    val imageUrl: String?,
    @field:Schema(description = "관심 카테고리 id. 보강 전이면 NULL", example = "3", nullable = true)
    val interestCategoryId: Int?,
    @field:Schema(description = "팝업 이름", example = "오래오래 함께가게")
    val title: String,
    @field:Schema(description = "운영 시작일. 없으면 NULL", example = "2026-09-20", nullable = true)
    val startDate: LocalDate?,
    @field:Schema(description = "운영 종료일. 없으면 NULL", example = "2026-10-12", nullable = true)
    val endDate: LocalDate?,
    @field:Schema(description = "입장 방식", example = "UNKNOWN")
    val reservationType: ReservationType,
    @field:Schema(description = "종료 여부. 종료일 < 오늘(KST). 종료일 없으면 false", example = "false")
    val ended: Boolean,
    @field:Schema(description = "찜한 시각(KST ISO-8601)", example = "2026-10-02T13:40:00+09:00")
    val wishedAt: OffsetDateTime,
) {
    companion object {
        fun from(wished: WishedPopup) =
            WishResponse(
                popupId = requireNotNull(wished.popup.id),
                imageUrl = wished.popup.imageUrls?.firstOrNull(),
                interestCategoryId = wished.popup.interestCategoryId,
                title = wished.popup.title,
                startDate = wished.popup.startDate,
                endDate = wished.popup.endDate,
                reservationType = wished.popup.reservationType,
                ended = wished.ended,
                wishedAt =
                    wished.wish.createdAt
                        .atZoneSameInstant(KST)
                        .toOffsetDateTime(),
            )
    }
}
