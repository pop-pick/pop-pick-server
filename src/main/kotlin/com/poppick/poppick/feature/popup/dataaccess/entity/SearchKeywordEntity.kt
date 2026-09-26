package com.poppick.poppick.feature.popup.dataaccess.entity

import com.poppick.poppick.feature.popup.domain.SearchKeyword
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

@Entity
@Table(name = "search_keyword")
class SearchKeywordEntity(
    /** 원천에 그대로 넘기는 완성 검색어. (targetSource, keyword) 로 유일. */
    @Column(nullable = false, length = 100)
    var keyword: String,
    /** 이 검색어를 사용할 탐색 원천. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var targetSource: SourceType,
    /** false 면 탐색 배치에서 제외. */
    @Column(nullable = false)
    var isActive: Boolean = true,
    /** 검색어 식별자(search_keyword_id). 저장 전엔 NULL. */
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
                isActive = searchKeyword.isActive,
                id = searchKeyword.id,
            )
    }

    fun toDomain() =
        SearchKeyword(
            keyword = keyword,
            targetSource = targetSource,
            isActive = isActive,
            id = id,
        )
}
