package com.poppick.poppick.global.paging

data class Slice<T>(
    val content: List<T>,
    val cursorable: Cursorable<*>,
    val hasNext: Boolean,
) {
    fun <U> map(converter: (T) -> U): Slice<U> = Slice(content.map(converter), cursorable, hasNext)
}
