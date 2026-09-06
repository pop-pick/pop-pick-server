package com.poppick.poppick.global.response

import com.poppick.poppick.global.paging.Slice

data class PageResponse<T>(
    val content: List<T>,
    val hasNext: Boolean,
) {
    companion object {
        fun <T> from(slice: Slice<T>) =
            PageResponse(
                content = slice.content,
                hasNext = slice.hasNext,
            )
    }
}
