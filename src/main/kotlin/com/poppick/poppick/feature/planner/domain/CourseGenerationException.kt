package com.poppick.poppick.feature.planner.domain

/** LLM 코스 응답이 재시도 후에도 검증을 통과하지 못했다. ErrorType 매핑은 호출 측(PlannerGenerator)에서 한다. */
class CourseGenerationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
