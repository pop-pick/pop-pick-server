package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.popup.domain.GeoPoint
import com.poppick.poppick.feature.popup.domain.Popup
import java.time.LocalDate
import java.time.LocalTime

/** 길찾기 · 방문 시각까지 붙은 최종 코스. planner · planner_popup 행이 된다. 시각은 전부 KST. */
data class PlannedCourse(
    val title: String,
    val summary: String,
    val visitDate: LocalDate,
    val startTime: LocalTime,
    /** 마지막 팝업 visitAt + stayMin. */
    val endTime: LocalTime,
    /** endTime - startTime(분). */
    val totalMin: Int,
    /** 도보 이동거리 합계(m). */
    val totalTravelM: Int,
    val stops: List<PlannedStop>,
)

data class PlannedStop(
    val popup: Popup,
    /** 1부터. */
    val visitOrder: Int,
    /** 도착 · 관람 시작 시각. */
    val visitAt: LocalTime,
    val stayMin: Int,
    val reason: String,
    /** 다음 팝업까지 도보(분, 올림). 마지막은 NULL. */
    val nextTravelMin: Int?,
    /** 다음 팝업까지 도보(m). 마지막은 NULL. */
    val nextTravelM: Int?,
    /** 다음 팝업까지 경로. 마지막은 NULL, 같은 건물이면 빈 리스트. */
    val nextPath: List<GeoPoint>?,
)
