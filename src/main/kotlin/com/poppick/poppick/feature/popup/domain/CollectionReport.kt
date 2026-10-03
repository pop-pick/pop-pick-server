package com.poppick.poppick.feature.popup.domain

import com.poppick.poppick.global.util.toSecondsText
import java.time.Duration
import java.time.LocalDate

/** 수집 배치 1회 실행 결과. 단계가 통째로 실패하면 해당 단계는 NULL. */
data class CollectionReport(
    val date: LocalDate,
    val collect: Collect?,
    val enrich: Enrich?,
    val image: Image?,
    val embed: Embed?,
    val elapsed: Duration,
) {
    fun summary() =
        "collection: date=$date collect=${collect?.let { "ok" } ?: "failed"} " +
            "enrich=${enrich?.let { "ok" } ?: "failed"} image=${image?.let { "ok" } ?: "failed"} " +
            "embed=${embed?.let { "ok" } ?: "failed"} in ${elapsed.toSecondsText()}"

    data class Collect(
        /** 실행한 검색어 수. */
        val keywords: Int,
        /** 검색 자체가 실패(또는 타임아웃)한 검색어 수. */
        val failedKeywords: Int,
        /** 후속 페이지 · 분할 칸 요청이 재시도까지 실패해 일부 결과만 받은 검색어 수. */
        val partialKeywords: Int,
        /** 분할 한도에서도 결과가 잘린 영역이 있던 검색어 수. */
        val truncatedKeywords: Int,
        /** 검색어별 결과 장소 총합(검색어 사이 중복 포함). */
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
            "collect: keywords=$keywords failedKeywords=$failedKeywords partialKeywords=$partialKeywords " +
                "truncatedKeywords=$truncatedKeywords fetched=$fetched seoul=$seoul unique=$unique " +
                "new=$created updated=$updated failed=$failed${if (timedOut) " TIMED_OUT" else ""} " +
                "in ${elapsed.toSecondsText()}"
    }

    data class Enrich(
        val targets: Int,
        /** targets 중 진행 중 갱신(핵심 필드가 차 있어도 refresh 간격이 지난 진행 중 팝업) 사유로 고른 대상. */
        val refreshed: Int,
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
            "enrich: targets=$targets refreshed=$refreshed enriched=$enriched notFound=$notFound failed=$failed skipped=$skipped " +
                "active=$active ended=$ended incomplete=$incomplete cost=$${"%.2f".format(costUsd)} searches=$searchCalls" +
                "${if (timedOut) " TIMED_OUT" else ""} in ${elapsed.toSecondsText()}"
    }

    data class Image(
        val targets: Int,
        /** 이미지를 1장 이상 저장한 건수. */
        val found: Int,
        /** 저장할 이미지를 못 찾아 image_checked_at 만 갱신한 건수(rejected 포함). */
        val notFound: Int,
        /** 처리 중 예외(Perplexity 실패 · 저장 실패 포함) 및 타임아웃. image_checked_at 은 갱신하지 않는다. */
        val failed: Int,
        /** found 중 출처 페이지 og:image 로 얻은 건수. */
        val fromOg: Int,
        /** found 중 Perplexity image_search 로 모델이 고른 이미지를 저장한 건수. */
        val fromModel: Int,
        /** 모델이 주소를 줬지만 검색 결과 목록에 없거나 이미지 확인에 실패해 버린 건수(notFound 의 일부). */
        val rejected: Int,
        /** 응답을 받은 Perplexity 요청의 비용 합계(USD, 실패 건 포함). */
        val costUsd: Double,
        val timedOut: Boolean,
        val elapsed: Duration,
    ) {
        fun summary() =
            "image: targets=$targets found=$found notFound=$notFound failed=$failed fromOg=$fromOg fromModel=$fromModel " +
                "rejected=$rejected cost=$${"%.2f".format(costUsd)}${if (timedOut) " TIMED_OUT" else ""} in ${elapsed.toSecondsText()}"
    }

    data class Embed(
        /** 임베딩 대상(종료가 확정된 팝업을 뺀 전부). */
        val targets: Int,
        /** 이번에 임베딩해 저장(insert · 갱신)한 건수. */
        val embedded: Int,
        /** 원문 해시가 같아 건너뛴 건수 + 타임아웃으로 요청하지 않은 건수. */
        val skipped: Int,
        /** 호출 · 저장 실패한 배치에 속한 건수. */
        val failed: Int,
        /** 응답을 받은 요청의 입력 토큰 합계. */
        val promptTokens: Int,
        /** USD */
        val costUsd: Double,
        val timedOut: Boolean,
        val elapsed: Duration,
    ) {
        fun summary() =
            "embed: targets=$targets embedded=$embedded skipped=$skipped failed=$failed tokens=$promptTokens " +
                "cost=$${"%.4f".format(costUsd)}${if (timedOut) " TIMED_OUT" else ""} in ${elapsed.toSecondsText()}"
    }
}
