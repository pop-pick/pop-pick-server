package com.poppick.poppick.feature.member.domain

/** 동행 유형. label 은 플래너 프롬프트에 그대로 들어간다. */
enum class AccompanyType(
    val label: String,
) {
    ALONE("혼자"),
    COUPLE("연인과"),
    WITH_FRIEND("친구와"),
    WITH_FAMILY("가족과"),
}
