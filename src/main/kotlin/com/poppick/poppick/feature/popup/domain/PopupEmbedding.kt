package com.poppick.poppick.feature.popup.domain

data class PopupEmbedding(
    /** 임베딩 대상 팝업 id(popup FK, 팝업 삭제 시 함께 삭제). */
    val popupId: Long,
    /** 임베딩 종류. 초기엔 PROFILE(팝업 프로필 전체) 하나. (popupId, kind, model) 로 유일. */
    val kind: String,
    /** 임베딩 생성 모델명. 예: text-embedding-3-small */
    val model: String,
    /** 실제로 임베딩한 원문 문자열. */
    val contentText: String,
    /** contentText 의 SHA-256 hex. 내용이 바뀌었을 때만 재임베딩하는 판단 기준. */
    val contentHash: String,
    /** 임베딩 벡터(1536 차원, pgvector). 코사인 유사도로 검색. */
    val embedding: FloatArray,
    /** 임베딩 식별자(popup_embedding_id). 저장 전엔 NULL. */
    val id: Long? = null,
) {
    companion object {
        /** 팝업 프로필 전체(PopupProfileText)를 임베딩한 것. */
        const val KIND_PROFILE = "PROFILE"
    }
}
