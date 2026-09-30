package com.poppick.poppick.feature.planner.domain

import java.time.format.DateTimeFormatter

/**
 * 캘린더 일정 본문(Google details · ICS DESCRIPTION 공통).
 * ```
 * {summary}
 *
 * 1. 14:00 오래오래 함께가게 (60분)
 *    서울 성동구 성수동2가 302-13
 *    → 도보 11분
 * 2. …
 *
 * POP PICK 에서 만든 코스
 * ```
 */
object CourseDescription {
    private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val INDENT = "   "

    fun build(planner: Planner): String {
        val stops =
            planner.stops.sortedBy { it.visitOrder }.flatMap { stop ->
                listOfNotNull(
                    "${stop.visitOrder}. ${stop.visitAt.format(HH_MM)} ${stop.title} (${stop.stayMin}분)",
                    stop.address?.takeIf { it.isNotBlank() }?.let { INDENT + it },
                    stop.nextTravelMin?.let { INDENT + if (it == 0) "→ 같은 건물" else "→ 도보 ${it}분" },
                )
            }
        return listOf(
            planner.summary,
            "",
            *stops.toTypedArray(),
            "",
            "POP PICK 에서 만든 코스",
        ).joinToString("\n")
    }

    /** 일정 장소: 첫 방문지 주소, 없으면 지역 이름. */
    fun location(
        planner: Planner,
        areaName: String,
    ) = planner.stops
        .minByOrNull { it.visitOrder }
        ?.address
        ?.takeIf { it.isNotBlank() } ?: areaName
}
