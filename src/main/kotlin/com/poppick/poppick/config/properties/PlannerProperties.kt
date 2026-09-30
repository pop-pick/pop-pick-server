package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

/** AI 플래너 설정. 값은 yml 의 planner.* 에서 받는다. */
@ConfigurationProperties("planner")
data class PlannerProperties(
    /** 벡터 검색으로 뽑는 후보 팝업 최대 수(LLM 에 넘길 후보). */
    val candidateLimit: Int = 40,
    /** 이 시간보다 오래된 DRAFT 는 DraftPurger 가 지운다. */
    val draftTtlHours: Long = 24,
    /** DraftPurger 실행 주기(Asia/Seoul 기준 cron). 스케줄러는 @Scheduled 의 플레이스홀더로 같은 키를 읽는다. */
    val draftPurgeCron: String = "0 15 * * * *",
)
