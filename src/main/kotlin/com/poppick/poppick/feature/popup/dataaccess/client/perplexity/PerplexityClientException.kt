package com.poppick.poppick.feature.popup.dataaccess.client.perplexity

import com.poppick.poppick.feature.popup.domain.PerplexityUsage

/** Perplexity 호출 실패(재시도 소진 · 4xx · 응답 잘림 · 응답 파싱 실패). 해당 팝업은 DB 반영 없이 건너뛴다. */
class PerplexityClientException(
    message: String,
    cause: Throwable? = null,
    /** 재시도 없이 거절된 4xx 의 상태 코드. 그 외 실패는 NULL. */
    val statusCode: Int? = null,
    /** 응답은 받았지만 쓸 수 없을 때의 사용량(비용 집계용). 응답을 못 받았으면 NULL. */
    val usage: PerplexityUsage? = null,
    /** status=incomplete 응답의 사유(max_output_tokens 등). */
    val incompleteReason: String? = null,
) : RuntimeException(message, cause) {
    /** 요청 자체가 잘못된 4xx(429 제외). 연속되면 설정 오류로 보고 보강 단계를 조기 종료한다. */
    val isClientError get() = statusCode != null && statusCode in 400..499
}
