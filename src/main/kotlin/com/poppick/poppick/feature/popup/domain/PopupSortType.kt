package com.poppick.poppick.feature.popup.domain

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType

/** 팝업 목록 정렬. */
enum class PopupSortType(
    /** 요청 파라미터(sort) 값. */
    val value: String,
) {
    /** 오픈일 최신순: start_date DESC NULLS LAST, popup_id DESC */
    LATEST("latest"),

    /** 인기순(상세 조회수): view_count DESC, popup_id DESC */
    POPULAR("popular"),
    ;

    companion object {
        val DEFAULT = LATEST

        /** 생략 · 공백이면 DEFAULT. 대소문자는 구분하지 않고, 알 수 없는 값이면 INVALID_PAGING_PARAMETER(400). */
        fun from(value: String?): PopupSortType {
            val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT
            return entries.firstOrNull { it.value.equals(normalized, ignoreCase = true) }
                ?: throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
        }
    }
}
