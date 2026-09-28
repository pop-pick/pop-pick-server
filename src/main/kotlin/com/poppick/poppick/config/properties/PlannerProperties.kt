package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** AI 플래너 설정. 값은 yml 의 planner.* 에서 받는다. */
@ConfigurationProperties("planner")
data class PlannerProperties(
    /** 벡터 검색으로 뽑는 후보 팝업 최대 수(LLM 에 넘길 후보). */
    val candidateLimit: Int = 40,
    /** 공유 링크 앞부분. shareUrl = "{shareBaseUrl}/{token}". 프론트 도메인 확정 시 바꾼다. */
    val shareBaseUrl: String = "https://pop-pick.app/share",
)
