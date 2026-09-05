package com.poppick.poppick.global.paging

@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class CursorDefault(
    val defaultLimit: Int = 10,
)
