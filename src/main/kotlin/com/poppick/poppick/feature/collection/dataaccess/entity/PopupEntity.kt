package com.poppick.poppick.feature.collection.dataaccess.entity

import com.poppick.poppick.feature.collection.domain.Popup
import com.poppick.poppick.feature.collection.domain.PopupCategory
import com.poppick.poppick.feature.collection.domain.ReservationType
import com.poppick.poppick.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `popup` 테이블. 원천 데이터를 합친 정규화 팝업이며, 중복 제거와 결손 필드 보강이 모두 이 테이블을 기준으로 이뤄진다.
 * 생성 · 수정 시각은 [BaseEntity] 의 createdAt · lastModifiedAt 이 담당한다.
 */
@Entity
@Table(
    name = "popup",
    indexes = [
        // 진행 중 팝업 조회용
        Index(name = "idx_popup_end_date_start_date", columnList = "end_date, start_date"),
        // 지역 · 카테고리 필터용
        Index(name = "idx_popup_area_code_category", columnList = "area_code, category"),
    ],
)
class PopupEntity(
    /** 팝업 이름 */
    @Column(nullable = false)
    var title: String,
    /** 대분류. TEXT 컬럼에 enum 이름 그대로 저장한다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: PopupCategory,
    /** 입장 방식. TEXT 컬럼에 enum 이름 그대로 저장한다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reservationType: ReservationType,
    /** 중복 판별 키. 이 값으로 팝업을 upsert 하므로 유일해야 한다 */
    @Column(unique = true)
    var dedupeKey: String,
    /** 주최 브랜드 · IP (예: 포켓몬, 젠틀몬스터) */
    var brand: String? = null,
    /** 팝업 소개 문구. 임베딩 텍스트의 핵심 재료 */
    var description: String? = null,
    /** 세부 키워드 (예: 굿즈, 포토존). PostgreSQL `text[]` 컬럼 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    var tags: MutableList<String> = mutableListOf(),
    /** 운영 시작일 */
    var startDate: LocalDate? = null,
    /** 운영 종료일. "진행 중" 판단은 이 값에서 파생한다 */
    var endDate: LocalDate? = null,
    /** 요일별 운영 시간 (예: `{"mon": "11:00-20:00"}`). `jsonb` 컬럼 */
    @JdbcTypeCode(SqlTypes.JSON)
    var openingHours: MutableMap<String, String> = mutableMapOf(),
    /** 상권 코드 (예: SEONGSU, HONGDAE). 지역 필터 · 플래너의 기준 단위 */
    var areaCode: String? = null,
    /** 도로명 주소 */
    var addressRoad: String? = null,
    /** 지번 주소 */
    var addressJibun: String? = null,
    /** 위도 */
    var latitude: Double? = null,
    /** 경도 */
    var longitude: Double? = null,
    /** 예약 페이지 링크 (네이버예약, 캐치테이블 등) */
    var reservationUrl: String? = null,
    /** 예약 오픈 시각. "오픈 10분 전 알림"의 기준 */
    var reservationOpenAt: LocalDateTime? = null,
    /** 입장료(원). 0 = 무료, null = 미확인 */
    var entryFee: Int? = null,
    /** 대표 이미지 URL 목록. PostgreSQL `text[]` 컬럼 */
    @JdbcTypeCode(SqlTypes.ARRAY)
    var imageUrls: MutableList<String> = mutableListOf(),
    /** 필드별 출처 (예: `{"end_date": "SEOUL_OPEN"}`). 보강 대상 필드를 골라내는 근거. `jsonb` 컬럼 */
    @JdbcTypeCode(SqlTypes.JSON)
    var fieldSources: MutableMap<String, String> = mutableMapOf(),
    /** 마지막 보강(Perplexity) 시각. 반복 호출 방지용 */
    var enrichedAt: LocalDateTime? = null,
    /** PK. DB 시퀀스가 채우므로 신규 팝업은 null */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun from(popup: Popup) =
            PopupEntity(
                title = popup.title,
                category = popup.category,
                reservationType = popup.reservationType,
                dedupeKey = popup.dedupeKey,
                brand = popup.brand,
                description = popup.description,
                tags = popup.tags.toMutableList(),
                startDate = popup.startDate,
                endDate = popup.endDate,
                openingHours = popup.openingHours.toMutableMap(),
                areaCode = popup.areaCode,
                addressRoad = popup.addressRoad,
                addressJibun = popup.addressJibun,
                latitude = popup.latitude,
                longitude = popup.longitude,
                reservationUrl = popup.reservationUrl,
                reservationOpenAt = popup.reservationOpenAt,
                entryFee = popup.entryFee,
                imageUrls = popup.imageUrls.toMutableList(),
                fieldSources = popup.fieldSources.toMutableMap(),
                enrichedAt = popup.enrichedAt,
                id = popup.id,
            )
    }

    /** 엔티티를 상위 레이어가 다루는 도메인 객체로 변환한다. */
    fun toDomain() =
        Popup(
            title = title,
            category = category,
            reservationType = reservationType,
            dedupeKey = dedupeKey,
            brand = brand,
            description = description,
            tags = tags.toList(),
            startDate = startDate,
            endDate = endDate,
            openingHours = openingHours.toMap(),
            areaCode = areaCode,
            addressRoad = addressRoad,
            addressJibun = addressJibun,
            latitude = latitude,
            longitude = longitude,
            reservationUrl = reservationUrl,
            reservationOpenAt = reservationOpenAt,
            entryFee = entryFee,
            imageUrls = imageUrls.toList(),
            fieldSources = fieldSources.toMap(),
            enrichedAt = enrichedAt,
            id = id,
        )
}
