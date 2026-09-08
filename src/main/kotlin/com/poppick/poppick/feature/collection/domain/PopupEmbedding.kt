package com.poppick.poppick.feature.collection.domain

import java.time.LocalDateTime

/**
 * 추천 · 유사 검색용 임베딩. "성수 + 캐릭터/F&B" 같은 취향 입력을 벡터로 바꿔 비슷한 팝업을 찾는 데 쓴다.
 * 팝업 1건에 관점([kind])과 모델([model])별로 여러 개를 붙일 수 있어 모델 교체 시 병행 운영이 가능하다.
 */
data class PopupEmbedding(
    /** 대상 팝업의 식별자 */
    val popupId: Long,
    /** 임베딩 관점. 초기에는 PROFILE 하나만 사용한다 */
    val kind: EmbeddingKind,
    /** 임베딩을 만든 모델명 (예: text-embedding-3-small). 모델을 교체해도 기존 임베딩과 구분된다 */
    val model: String,
    /** 실제로 임베딩한 문자열. 디버깅 · 재현용 */
    val contentText: String,
    /** contentText 의 SHA-256. 값이 같으면 재임베딩을 생략해 OpenAI 호출을 줄인다 */
    val contentHash: String,
    /** 임베딩 벡터(1536차원) */
    val embedding: FloatArray,
    /** 생성 시각 */
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /** 임베딩 식별자. 아직 저장되지 않은 임베딩은 null */
    val id: Long? = null,
) {
    /** 팝업 내용이 그대로면 재임베딩할 필요가 없다. */
    fun hasSameContent(contentHash: String): Boolean = this.contentHash == contentHash

    // FloatArray 는 내용이 아니라 참조로 비교되어 data class 의 기본 equals 가 의미를 갖지 못한다.
    // 저장된 임베딩끼리는 식별자로 비교한다.
    override fun equals(other: Any?): Boolean = this === other || (other is PopupEmbedding && other.id != null && other.id == id)

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
