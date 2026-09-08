package com.poppick.poppick.feature.collection.dataaccess.entity

import com.poppick.poppick.feature.collection.domain.SearchKeyword
import com.poppick.poppick.feature.collection.domain.SourceType
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
import jakarta.persistence.UniqueConstraint

/**
 * `search_keyword` 테이블. 탐색 배치용 검색어를 보관한다.
 * 생성 · 수정 시각은 [BaseEntity] 의 createdAt · lastModifiedAt 이 담당한다.
 */
@Entity
@Table(
    name = "search_keyword",
    // 같은 원천에 같은 검색어를 두 번 넣지 않는다
    uniqueConstraints = [
        UniqueConstraint(name = "uk_search_keyword_target_source_keyword", columnNames = ["target_source", "keyword"]),
    ],
    // 배치 조회(WHERE target_source = ? AND is_active)용
    indexes = [Index(name = "idx_search_keyword_target_source_is_active", columnList = "target_source, is_active")],
)
class SearchKeywordEntity(
    /** 원천 API 에 그대로 넣는 완성 검색어 */
    @Column(nullable = false)
    var keyword: String,
    /** 이 검색어를 쓰는 원천. TEXT 컬럼에 enum 이름 그대로 저장하며 DB CHECK 제약이 KAKAO_MAP · PERPLEXITY 로 제한한다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var targetSource: SourceType,
    /**
     * false 면 배치에서 제외한다. 삭제 대신 비활성화로 이력을 남긴다.
     * 프로퍼티 이름을 `isActive` 로 두면 Kotlin 이 만드는 게터(`isActive()`)와 JPA · Spring Data 의
     * 프로퍼티 해석이 어긋날 수 있어, 필드는 `active` 로 두고 컬럼명을 명시한다.
     */
    @Column(name = "is_active", nullable = false)
    var active: Boolean = true,
    /** PK. DB 시퀀스가 채우므로 신규 검색어는 null */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "search_keyword_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun from(searchKeyword: SearchKeyword) =
            SearchKeywordEntity(
                keyword = searchKeyword.keyword,
                targetSource = searchKeyword.targetSource,
                active = searchKeyword.isActive,
                id = searchKeyword.id,
            )
    }

    /** 엔티티를 상위 레이어가 다루는 도메인 객체로 변환한다. */
    fun toDomain() =
        SearchKeyword(
            keyword = keyword,
            targetSource = targetSource,
            isActive = active,
            id = id,
        )
}
