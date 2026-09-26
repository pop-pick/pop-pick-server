package com.poppick.poppick.feature.popup.domain

import com.poppick.poppick.global.util.toSecondsText
import java.time.Duration
import java.time.LocalDate

/** 수집 배치 1회 실행 결과. 단계가 통째로 실패하면 해당 단계는 NULL. */
data class CollectionReport(
    val date: LocalDate,
    val collect: Collect?,
    val enrich: Enrich?,
    val elapsed: Duration,
) {
    fun summary() =
        "collection: date=$date collect=${collect?.let { "ok" } ?: "failed"} " +
            "enrich=${enrich?.let { "ok" } ?: "failed"} in ${elapsed.toSecondsText()}"

    data class Collect(
        /** 실행한 검색어 수. */
        val keywords: Int,
        /** 검색 자체가 실패(또는 타임아웃)한 검색어 수. */
        val failedKeywords: Int,
        /** 검색 결과 장소 총합(중복 포함). */
        val fetched: Int,
        /** 서울 필터 통과. */
        val seoul: Int,
        /** place id 중복 제거 후. */
        val unique: Int,
        val created: Int,
        val updated: Int,
        /** 저장 중 예외. */
        val failed: Int,
        val timedOut: Boolean,
        val elapsed: Duration,
    ) {
        fun summary() =
            "collect: keywords=$keywords failedKeywords=$failedKeywords fetched=$fetched seoul=$seoul unique=$unique " +
                "new=$created updated=$updated failed=$failed${if (timedOut) " TIMED_OUT" else ""} " +
                "in ${elapsed.toSecondsText()}"
    }

    data class Enrich(
        val targets: Int,
        /** 이 장소의 팝업 정보를 받아 병합한 건수. */
        val enriched: Int,
        /** 정보를 못 찾았거나(found=false) 다른 팝업을 가져온(matches_place=false) 건수. */
        val notFound: Int,
        /** 호출 · 파싱 · 저장 실패(DB 미반영) 및 타임아웃. */
        val failed: Int,
        /** 4xx 연속 · 타임아웃으로 제출하지 않은 대상. */
        val skipped: Int,
        /** 이번에 보강한 팝업의 분포(로그용 계산값, 저장하지 않는다). 기간 · 카테고리가 모두 있고 종료일 >= 오늘. */
        val active: Int,
        /** 종료일 < 오늘. */
        val ended: Int,
        /** 그 외(핵심 필드가 하나라도 빔). */
        val incomplete: Int,
        /** 응답을 받은 요청의 비용 합계(USD, 실패 건 포함). */
        val costUsd: Double,
        /** 웹 검색 호출 횟수 합계. */
        val searchCalls: Int,
        val timedOut: Boolean,
        val elapsed: Duration,
    ) {
        fun summary() =
            "enrich: targets=$targets enriched=$enriched notFound=$notFound failed=$failed skipped=$skipped " +
                "active=$active ended=$ended incomplete=$incomplete cost=$${"%.2f".format(costUsd)} searches=$searchCalls" +
                "${if (timedOut) " TIMED_OUT" else ""} in ${elapsed.toSecondsText()}"
    }
}
