package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.feature.popup.domain.Popup

/** LLM 이 고른 코스(검증 · 정리 후). 이동시간 · 방문 시각은 다음 단계(길찾기)에서 붙는다. */
data class CourseDraft(
    /** 코스 이름(20자 이내). */
    val title: String,
    /** 코스 한 줄 소개(80자 이내). */
    val summary: String,
    /** 방문 순서. */
    val stops: List<CourseStop>,
)

data class CourseStop(
    val popup: Popup,
    /** 체류시간(분). 20~120, 10분 단위. */
    val stayMin: Int,
    /** 추천 이유 한 문장(60자 이내). */
    val reason: String,
)
