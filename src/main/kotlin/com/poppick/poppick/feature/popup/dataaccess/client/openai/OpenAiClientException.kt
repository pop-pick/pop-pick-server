package com.poppick.poppick.feature.popup.dataaccess.client.openai

/** OpenAI 호출 실패(재시도 소진 · 4xx · 응답 파싱 실패 · 차원 불일치). 해당 배치는 DB 반영 없이 건너뛴다. */
class OpenAiClientException(
    message: String,
    cause: Throwable? = null,
    /** 재시도 없이 거절된 4xx 의 상태 코드. 그 외 실패는 NULL. */
    val statusCode: Int? = null,
) : RuntimeException(message, cause)
