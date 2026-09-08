package com.poppick.poppick.feature.collection.dataaccess.entity

import com.poppick.poppick.feature.collection.domain.PopupSource
import com.poppick.poppick.feature.collection.domain.SourceType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * `popup_source` 테이블. 원천 응답을 가공 없이 보관한다.
 * 수집 시각만 필요해 생성 · 수정 시각을 갖는 BaseEntity 는 상속하지 않는다.
 */
@Entity
@Table(
    name = "popup_source",
    // 같은 원천의 같은 항목을 중복 저장하지 않는다
    uniqueConstraints = [UniqueConstraint(name = "uk_popup_source_source_external_id", columnNames = ["source", "external_id"])],
)
class PopupSourceEntity(
    /** 원천 구분(카카오맵 · 서울열린데이터 · Perplexity). TEXT 컬럼에 enum 이름 그대로 저장한다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: SourceType,
    /** 원천 응답 원문. `jsonb` 컬럼 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var rawPayload: MutableMap<String, Any?>,
    /** 원천 측 식별자 (카카오 place id 등). Perplexity 응답에는 없어 null */
    var externalId: String? = null,
    /**
     * 매칭된 정규화 팝업의 PK. 정규화 전에는 null 이다.
     * 연관관계 대신 식별자만 들고 있어 popup 테이블과의 결합을 줄인다.
     */
    @Column(name = "popup_id")
    var popupId: Long? = null,
    /** 수집 시각 */
    var fetchedAt: LocalDateTime = LocalDateTime.now(),
    /** PK. DB 시퀀스가 채우므로 신규 원본은 null */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_source_id")
    var id: Long? = null,
) {
    companion object {
        fun from(popupSource: PopupSource) =
            PopupSourceEntity(
                source = popupSource.source,
                rawPayload = popupSource.rawPayload.toMutableMap(),
                externalId = popupSource.externalId,
                popupId = popupSource.popupId,
                fetchedAt = popupSource.fetchedAt,
                id = popupSource.id,
            )
    }

    /** 엔티티를 상위 레이어가 다루는 도메인 객체로 변환한다. */
    fun toDomain() =
        PopupSource(
            source = source,
            rawPayload = rawPayload.toMap(),
            externalId = externalId,
            popupId = popupId,
            fetchedAt = fetchedAt,
            id = id,
        )
}
