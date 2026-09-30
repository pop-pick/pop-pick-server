package com.poppick.poppick.global.response

import com.poppick.poppick.global.paging.Slice

data class PageResponse<T>(
    val content: List<T>,
    val hasNext: Boolean,
    /** 다음 페이지 커서(문자열 keyset 커서를 쓰는 목록만). 마지막 페이지면 NULL. */
    val nextCursor: String? = null,
) {
    companion object {
        fun <T> from(slice: Slice<T>) =
            PageResponse(
                content = slice.content,
                hasNext = slice.hasNext,
            )
    }
}
