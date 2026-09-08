package com.poppick.poppick.feature.collection.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 세 원천(카카오맵 · 서울열린데이터 · Perplexity)을 하나의 공통 스키마로 합친 정규화 팝업.
 * 서비스(탐색 · 상세 · 플래너 · 추천)는 원천을 모른 채 이 객체만 다룬다.
 */
data class Popup(
    /** 팝업 이름 */
    val title: String,
    /** 대분류. 탐색 필터와 임베딩 텍스트에 쓰인다 */
    val category: PopupCategory,
    /** 입장 방식(자유입장 · 사전예약 · 현장대기) */
    val reservationType: ReservationType,
    /**
     * 중복 판별 키. `정규화한 title + 좌표(소수 4자리)` 또는 여기에 `startDate` 를 더해 만든다.
     * 원천이 달라도 같은 팝업이면 같은 값이 나오므로, 임베딩 없이 결정적으로 upsert 할 수 있다.
     */
    val dedupeKey: String,
    /** 주최 브랜드 · IP (예: 포켓몬, 젠틀몬스터) */
    val brand: String? = null,
    /** 팝업 소개 문구. 임베딩 텍스트의 핵심 재료 */
    val description: String? = null,
    /** 세부 키워드 (예: 굿즈, 포토존, 한정판). 필터 · 임베딩에 사용 */
    val tags: List<String> = emptyList(),
    /** 운영 시작일 */
    val startDate: LocalDate? = null,
    /** 운영 종료일. "진행 중" 여부는 별도 상태 값 없이 이 날짜에서 파생한다 */
    val endDate: LocalDate? = null,
    /** 요일별 운영 시간 (예: `{"mon": "11:00-20:00"}`) */
    val openingHours: Map<String, String> = emptyMap(),
    /** 상권 코드 (예: SEONGSU, HONGDAE, THE_HYUNDAI). 지역 필터 · 플래너의 기준 단위 */
    val areaCode: String? = null,
    /** 도로명 주소 */
    val addressRoad: String? = null,
    /** 지번 주소 */
    val addressJibun: String? = null,
    /** 위도. 지도 표시 · 동선 계산용 */
    val latitude: Double? = null,
    /** 경도. 지도 표시 · 동선 계산용 */
    val longitude: Double? = null,
    /** 예약 페이지 링크 (네이버예약, 캐치테이블 등) */
    val reservationUrl: String? = null,
    /** 예약 오픈 시각. "오픈 10분 전 알림" 기능의 기준 */
    val reservationOpenAt: LocalDateTime? = null,
    /** 입장료(원). 0 이면 무료, null 이면 미확인 */
    val entryFee: Int? = null,
    /** 대표 이미지 URL 목록 */
    val imageUrls: List<String> = emptyList(),
    /**
     * 필드별 출처 (예: `{"end_date": "SEOUL_OPEN", "reservation_url": "PERPLEXITY"}`).
     * 보강 단계에서 결손 · 저신뢰 필드만 골라내는 근거가 된다.
     */
    val fieldSources: Map<String, String> = emptyMap(),
    /** 마지막 보강(Perplexity) 시각. null 이면 아직 보강하지 않은 팝업이며, 반복 호출을 막는 기준이 된다 */
    val enrichedAt: LocalDateTime? = null,
    /** 팝업 고유 식별자. 아직 저장되지 않은 팝업은 null */
    val id: Long? = null,
) {
    /** 별도 상태 컬럼 없이 운영 기간에서 진행 여부를 파생한다. 날짜가 비어 있으면 열린 구간으로 본다. */
    fun isOngoing(baseDate: LocalDate): Boolean =
        (endDate == null || !endDate.isBefore(baseDate)) &&
            (startDate == null || !startDate.isAfter(baseDate))

    /** 입장료가 0원으로 확인된 팝업. 미확인(null)은 무료로 보지 않는다 */
    val isFree: Boolean
        get() = entryFee == 0
}
