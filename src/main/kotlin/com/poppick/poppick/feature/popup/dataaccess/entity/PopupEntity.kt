package com.poppick.poppick.feature.popup.dataaccess.entity

import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.ReservationType
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "popup")
class PopupEntity(
    /** 최초 생성 원천. 이후 다른 원천으로 병합돼도 바뀌지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: SourceType,
    /** 원천 측 식별자. 카카오는 place id, Perplexity 는 NULL. (source, externalId) 로 유일. */
    var externalId: String? = null,
    /** 정보 근거 URL 목록. 병합 시 합집합으로 누적. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var sourceUrls: List<String>? = null,
    /** 마지막으로 받은 원천 응답 원문(JSON). 재가공 · 디버깅용. */
    @JdbcTypeCode(SqlTypes.JSON)
    var rawPayload: Map<String, Any?>? = null,
    /** 팝업 이름. */
    @Column(nullable = false, length = 500)
    var title: String,
    /** 주최 브랜드 또는 IP(캐릭터 · 작품 등). */
    @Column(length = 500)
    var brand: String? = null,
    /** 관심 카테고리 id(interest_category FK). 1 캐릭터/IP · 2 패션/브랜드 · 3 F&B · 4 전시/아트 · 5 뷰티 · 6 게임/엔터 · 7 라이프스타일 · 8 기타. 보강 전엔 NULL. */
    var interestCategoryId: Int? = null,
    /** 팝업 소개 문구. */
    @Column(columnDefinition = "text")
    var description: String? = null,
    /** 세부 키워드 목록. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var tags: List<String>? = null,
    /** 대표 이미지 URL 목록. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    var imageUrls: List<String>? = null,
    /** 운영 시작일. */
    var startDate: LocalDate? = null,
    /** 운영 종료일. 종료 여부는 저장하지 않고 조회 시 end_date < today 로 계산한다. */
    var endDate: LocalDate? = null,
    /** 운영시간 · 휴무 한 줄. 예: "매일 11:00~20:00, 월 휴무" */
    @Column(columnDefinition = "text")
    var openingHours: String? = null,
    /** 입장 방식(예약 · 웨이팅 등). 확인 전엔 UNKNOWN. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reservationType: ReservationType = ReservationType.UNKNOWN,
    /** 예약 페이지 URL. */
    @Column(length = 2000)
    var reservationUrl: String? = null,
    /** 예약 오픈 시각(타임존 포함). */
    var reservationOpenAt: OffsetDateTime? = null,
    /** 입장료(원). 0 = 무료, NULL = 미확인. */
    var entryFee: Int? = null,
    /** 카카오 place id. 카카오 수집분은 팝업 자체, Perplexity 수집분은 해석된 venue 의 id. */
    var placeId: String? = null,
    /** 카카오 place 이름. */
    var placeName: String? = null,
    /** 도로명 주소. */
    var addressRoad: String? = null,
    /** 지번 주소. */
    var addressJibun: String? = null,
    /** 위도. */
    var latitude: Double? = null,
    /** 경도. */
    var longitude: Double? = null,
    /** 장소를 얼마나 정확히 특정했는지(EXACT · VENUE · UNRESOLVED). */
    @Enumerated(EnumType.STRING)
    var placeResolution: PlaceResolution? = null,
    /** 보강 후에도 핵심 필드(기간 · 카테고리)를 다 채우지 못한 횟수. 한도 미만이고 핵심 필드가 비면 재보강 대상. */
    @Column(nullable = false)
    var enrichRetryCount: Int = 0,
    /** 마지막 보강 시각(타임존 포함). NULL 이면 아직 보강하지 않은 팝업. */
    var enrichedAt: OffsetDateTime? = null,
    /** 팝업 식별자(popup_id). 저장 전엔 NULL. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun from(popup: Popup) =
            PopupEntity(
                source = popup.source,
                externalId = popup.externalId,
                sourceUrls = popup.sourceUrls,
                rawPayload = popup.rawPayload,
                title = popup.title,
                brand = popup.brand,
                interestCategoryId = popup.interestCategoryId,
                description = popup.description,
                tags = popup.tags,
                imageUrls = popup.imageUrls,
                startDate = popup.startDate,
                endDate = popup.endDate,
                openingHours = popup.openingHours,
                reservationType = popup.reservationType,
                reservationUrl = popup.reservationUrl,
                reservationOpenAt = popup.reservationOpenAt,
                entryFee = popup.entryFee,
                placeId = popup.placeId,
                placeName = popup.placeName,
                addressRoad = popup.addressRoad,
                addressJibun = popup.addressJibun,
                latitude = popup.latitude,
                longitude = popup.longitude,
                placeResolution = popup.placeResolution,
                enrichRetryCount = popup.enrichRetryCount,
                enrichedAt = popup.enrichedAt,
                id = popup.id,
            )
    }

    fun toDomain() =
        Popup(
            source = source,
            externalId = externalId,
            sourceUrls = sourceUrls,
            rawPayload = rawPayload,
            title = title,
            brand = brand,
            interestCategoryId = interestCategoryId,
            description = description,
            tags = tags,
            imageUrls = imageUrls,
            startDate = startDate,
            endDate = endDate,
            openingHours = openingHours,
            reservationType = reservationType,
            reservationUrl = reservationUrl,
            reservationOpenAt = reservationOpenAt,
            entryFee = entryFee,
            placeId = placeId,
            placeName = placeName,
            addressRoad = addressRoad,
            addressJibun = addressJibun,
            latitude = latitude,
            longitude = longitude,
            placeResolution = placeResolution,
            enrichRetryCount = enrichRetryCount,
            enrichedAt = enrichedAt,
            id = id,
        )
}
