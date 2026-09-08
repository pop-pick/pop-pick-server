package com.poppick.poppick.feature.collection.domain

import java.time.LocalDateTime

/**
 * 가공 전 원천 응답. 원본을 남겨두면 정규화 로직이 바뀌어도 원천 API를 다시 호출하지 않고 재처리할 수 있고,
 * 같은 팝업이 여러 원천에서 발견됐다는 사실 자체가 신뢰도 근거가 된다.
 */
data class PopupSource(
    /** 이 원본을 어디서 받아왔는지 */
    val source: SourceType,
    /** 원천 응답 원문. 가공하지 않고 그대로 보관한다 */
    val rawPayload: Map<String, Any?>,
    /** 원천 측 식별자 (카카오 place id 등). Perplexity 응답에는 없다 */
    val externalId: String? = null,
    /** 매칭된 정규화 팝업의 식별자. 정규화 단계를 거치기 전에는 null */
    val popupId: Long? = null,
    /** 수집 시각 */
    val fetchedAt: LocalDateTime = LocalDateTime.now(),
    /** 원본 레코드 식별자. 아직 저장되지 않은 원본은 null */
    val id: Long? = null,
) {
    /** 정규화 단계에서 이 원본을 팝업에 연결한다. */
    fun matchTo(popupId: Long): PopupSource = copy(popupId = popupId)
}
