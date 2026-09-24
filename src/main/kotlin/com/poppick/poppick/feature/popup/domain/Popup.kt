package com.poppick.poppick.feature.popup.domain

import java.time.LocalDate
import java.time.OffsetDateTime

data class Popup(
    /** 최초 생성 원천. 이후 다른 원천으로 병합돼도 바뀌지 않는다. */
    val source: SourceType,
    /** 원천 측 식별자. 카카오는 place id, Perplexity 는 NULL. (source, externalId) 로 유일. */
    val externalId: String? = null,
    /** 정보 근거 URL 목록. 병합 시 합집합으로 누적. */
    val sourceUrls: List<String>? = null,
    /** 마지막으로 받은 원천 응답 원문(JSON). 재가공 · 디버깅용. */
    val rawPayload: Map<String, Any?>? = null,
    /** 팝업 이름. */
    val title: String,
    /** 주최 브랜드 또는 IP(캐릭터 · 작품 등). */
    val brand: String? = null,
    /** 관심 카테고리 id(interest_category FK). 1 캐릭터/IP · 2 패션/브랜드 · 3 F&B · 4 전시/아트 · 5 뷰티 · 6 게임/엔터 · 7 라이프스타일 · 8 기타. 보강 전엔 NULL. */
    val interestCategoryId: Int? = null,
    /** 팝업 소개 문구. */
    val description: String? = null,
    /** 세부 키워드 목록. */
    val tags: List<String>? = null,
    /** 대표 이미지 URL 목록. */
    val imageUrls: List<String>? = null,
    /** 운영 시작일. */
    val startDate: LocalDate? = null,
    /** 운영 종료일. 종료 여부는 저장하지 않고 조회 시 end_date < today 로 계산한다. */
    val endDate: LocalDate? = null,
    /** 요일별 운영 시간. 예: {"mon": "11:00-20:00"} */
    val openingHours: Map<String, String>? = null,
    /** 입장 방식(예약 · 웨이팅 등). 확인 전엔 UNKNOWN. */
    val reservationType: ReservationType = ReservationType.UNKNOWN,
    /** 예약 페이지 URL. */
    val reservationUrl: String? = null,
    /** 예약 오픈 시각(타임존 포함). */
    val reservationOpenAt: OffsetDateTime? = null,
    /** 입장료(원). 0 = 무료, NULL = 미확인. */
    val entryFee: Int? = null,
    /** 카카오 place id. 카카오 수집분은 팝업 자체, Perplexity 수집분은 해석된 venue 의 id. */
    val placeId: String? = null,
    /** 카카오 place 이름. */
    val placeName: String? = null,
    /** 도로명 주소. */
    val addressRoad: String? = null,
    /** 지번 주소. */
    val addressJibun: String? = null,
    /** 위도. */
    val latitude: Double? = null,
    /** 경도. */
    val longitude: Double? = null,
    /** 장소를 얼마나 정확히 특정했는지(EXACT · VENUE · UNRESOLVED). */
    val placeResolution: PlaceResolution? = null,
    /**
     * 보강 후에도 핵심 필드(기간 · 카테고리)를 다 채우지 못한 횟수(정보를 못 찾은 경우 포함).
     * 한도 미만이고 핵심 필드가 비어 있으면 재보강 대상이 된다.
     */
    val enrichRetryCount: Int = 0,
    /** 마지막 보강 시각(타임존 포함). NULL 이면 아직 보강하지 않은 팝업. */
    val enrichedAt: OffsetDateTime? = null,
    /** 팝업 식별자(popup_id). 저장 전엔 NULL. */
    val id: Long? = null,
) {
    /** 핵심 필드(시작일 · 종료일 · 카테고리)가 모두 채워졌는지. 하나라도 비면 재보강 대상 후보. */
    fun hasCoreFields() = startDate != null && endDate != null && interestCategoryId != null
}
