package com.poppick.poppick.feature.popup.dataaccess.entity

import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import com.poppick.poppick.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Array
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "popup_embedding")
class PopupEmbeddingEntity(
    /** 임베딩 대상 팝업 id(popup FK, 팝업 삭제 시 함께 삭제). */
    @Column(nullable = false)
    var popupId: Long,
    /** 임베딩 종류. 초기엔 PROFILE(팝업 프로필 전체) 하나. (popupId, kind, model) 로 유일. */
    @Column(nullable = false)
    var kind: String,
    /** 임베딩 생성 모델명. 예: text-embedding-3-small */
    @Column(nullable = false)
    var model: String,
    /** 실제로 임베딩한 원문 문자열. */
    @Column(nullable = false, columnDefinition = "text")
    var contentText: String,
    /** contentText 의 SHA-256 hex. 내용이 바뀌었을 때만 재임베딩하는 판단 기준. */
    @Column(nullable = false)
    var contentHash: String,
    /** 임베딩 벡터(1536 차원, pgvector). 코사인 유사도로 검색. */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(nullable = false)
    var embedding: FloatArray,
    /** 임베딩 식별자(popup_embedding_id). 저장 전엔 NULL. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_embedding_id")
    var id: Long? = null,
) : BaseEntity() {
    companion object {
        fun from(embedding: PopupEmbedding) =
            PopupEmbeddingEntity(
                popupId = embedding.popupId,
                kind = embedding.kind,
                model = embedding.model,
                contentText = embedding.contentText,
                contentHash = embedding.contentHash,
                embedding = embedding.embedding,
                id = embedding.id,
            )
    }

    fun toDomain() =
        PopupEmbedding(
            popupId = popupId,
            kind = kind,
            model = model,
            contentText = contentText,
            contentHash = contentHash,
            embedding = embedding,
            id = id,
        )
}
