package com.poppick.poppick.feature.planner.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 구글 캘린더 "일정 추가" 링크. 시각은 KST 로컬 시각 + ctz=Asia/Seoul. */
object GoogleCalendarLink {
    private const val BASE = "https://calendar.google.com/calendar/render"
    private const val TIME_ZONE = "Asia/Seoul"
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun build(
        planner: Planner,
        areaName: String,
    ): String {
        val dates = "${local(planner.visitDate, planner.startTime)}/${local(planner.visitDate, planner.endTime)}"
        val params =
            listOf(
                "action" to "TEMPLATE",
                "text" to planner.title,
                "dates" to dates,
                "ctz" to TIME_ZONE,
                "details" to CourseDescription.build(planner),
                "location" to CourseDescription.location(planner, areaName),
            )
        return BASE + "?" + params.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    }

    private fun local(
        date: LocalDate,
        time: LocalTime,
    ) = date.atTime(time).format(DATE_TIME)

    // URLEncoder 는 공백을 + 로 바꾸는데 구글 캘린더는 details 의 + 를 그대로 보여줘서 %20 으로 바꾼다.
    private fun encode(value: String) = URLEncoder.encode(value, UTF_8).replace("+", "%20")
}
