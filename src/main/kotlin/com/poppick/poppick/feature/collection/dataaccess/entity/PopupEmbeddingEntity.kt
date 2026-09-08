package com.poppick.poppick.feature.collection.dataaccess.entity

import com.poppick.poppick.feature.collection.domain.EmbeddingKind
import com.poppick.poppick.feature.collection.domain.PopupEmbedding
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Array
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

/**
 * `popup_embedding` 테이블. 추천 · 유사 검색용 벡터를 팝업과 1:N 으로 보관한다.
 * 생성 시각만 필요해 BaseEntity 는 상속하지 않으며, 팝업이 삭제되면 DB 의 CASCADE 로 함께 지워진다.
 */
@Entity
@Table(
    name = "popup_embedding",
    uniqueConstraints = [
        // 한 팝업에 같은 관점 · 같은 모델의 임베딩은 하나만 둔다
        UniqueConstraint(name = "uk_popup_embedding_popup_id_kind_model", columnNames = ["popup_id", "kind", "model"]),
    ],
)
class PopupEmbeddingEntity(
    /** 대상 팝업의 PK. 연관관계 대신 식별자만 들고 있다 */
    @Column(name = "popup_id", nullable = false)
    var popupId: Long,
    /** 임베딩 관점. TEXT 컬럼에 enum 이름 그대로 저장한다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: EmbeddingKind,
    /** 사용한 모델명 (예: text-embedding-3-small). 모델 교체 시 구분자가 된다 */
    @Column(nullable = false)
    var model: String,
    /** 실제로 임베딩한 문자열. 디버깅 · 재현용 */
    @Column(nullable = false)
    var contentText: String,
    /** contentText 의 SHA-256. 값이 같으면 재임베딩을 생략한다 */
    @Column(nullable = false)
    var contentHash: String,
    /** 임베딩 벡터. pgvector 의 `vector(1536)` 컬럼에 매핑된다 */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    @Column(nullable = false)
    var embedding: FloatArray,
    /** 생성 시각 */
    var createdAt: LocalDateTime = LocalDateTime.now(),
    /** PK. DB 시퀀스가 채우므로 신규 임베딩은 null */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_embedding_id")
    var id: Long? = null,
) {
    companion object {
        /** 임베딩 차원 수. 컬럼 정의(`vector(1536)`)와 반드시 같아야 한다 */
        const val EMBEDDING_DIMENSION = 1536

        fun from(popupEmbedding: PopupEmbedding) =
            PopupEmbeddingEntity(
                popupId = popupEmbedding.popupId,
                kind = popupEmbedding.kind,
                model = popupEmbedding.model,
                contentText = popupEmbedding.contentText,
                contentHash = popupEmbedding.contentHash,
                embedding = popupEmbedding.embedding.copyOf(),
                createdAt = popupEmbedding.createdAt,
                id = popupEmbedding.id,
            )
    }

    /** 엔티티를 상위 레이어가 다루는 도메인 객체로 변환한다. */
    fun toDomain() =
        PopupEmbedding(
            popupId = popupId,
            kind = kind,
            model = model,
            contentText = contentText,
            contentHash = contentHash,
            embedding = embedding.copyOf(),
            createdAt = createdAt,
            id = id,
        )
}
