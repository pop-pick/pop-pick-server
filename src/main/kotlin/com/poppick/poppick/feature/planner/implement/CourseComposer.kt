package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.planner.domain.CandidatePopup
import com.poppick.poppick.feature.planner.domain.CourseCondition
import com.poppick.poppick.feature.planner.domain.CourseDraft
import com.poppick.poppick.feature.planner.domain.CourseGenerationException
import com.poppick.poppick.feature.planner.domain.CoursePrompt
import com.poppick.poppick.feature.planner.domain.CourseStop
import com.poppick.poppick.feature.planner.domain.DurationType
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiChatClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets.UTF_8

private val log = KotlinLogging.logger { }

/**
 * 후보 팝업 중에서 LLM 이 방문할 팝업 · 순서 · 체류시간 · 추천 이유를 고르게 하고, 응답을 검증 · 정리한다.
 * - 후보에 없는 popupId · 중복은 버리고 나머지로 진행한다. maxStops 초과는 앞에서부터 자른다.
 * - 남은 개수가 minStops 미만이거나, JSON 파싱 실패 · 빈 문구면 사유를 붙여 1회 재시도하고, 또 실패하면 CourseGenerationException.
 * - OpenAI 호출 자체의 실패(OpenAiClientException, 전송 재시도는 클라이언트가 한다)는 재시도하지 않고 그대로 던진다.
 */
@Component
class CourseComposer(
    private val openAiChatClient: OpenAiChatClient,
    private val interestCategoryReader: InterestCategoryReader,
    private val jsonMapper: JsonMapper,
) {
    companion object {
        private const val SCHEMA_NAME = "planner_course"
        private const val INSTRUCTIONS_PATH = "prompts/planner-course-instructions.txt"
        private const val SCHEMA_PATH = "prompts/planner-course-schema.json"

        const val MIN_STAY_MIN = 20
        const val MAX_STAY_MIN = 120
        private const val STAY_UNIT_MIN = 10

        /** 체류 합계 상한 = 예산 × 이 비율(나머지는 이동시간). */
        private const val STAY_BUDGET_RATIO = 0.7

        private const val MAX_TITLE_LENGTH = 20
        private const val MAX_SUMMARY_LENGTH = 80
        private const val MAX_REASON_LENGTH = 60
        private const val MAX_ATTEMPTS = 2
    }

    private val instructions = ClassPathResource(INSTRUCTIONS_PATH).getContentAsString(UTF_8).trim()
    private val schema: JsonNode = ClassPathResource(SCHEMA_PATH).inputStream.use { jsonMapper.readTree(it) }

    fun compose(
        condition: CourseCondition,
        candidates: List<CandidatePopup>,
    ): CourseDraft {
        // 후보 부족은 호출 측이 먼저 422 로 걸러야 한다. 여기서는 방어만.
        require(candidates.size >= condition.durationType.minStops) {
            "후보 ${candidates.size}건은 최소 방문 개수 ${condition.durationType.minStops}곳 미만"
        }

        val categoryNames = interestCategoryReader.findAll().associate { it.id to it.category }
        val input = CoursePrompt.build(condition, candidates, categoryNames)

        var problems = emptyList<String>()
        for (attempt in 1..MAX_ATTEMPTS) {
            val retryInput = if (problems.isEmpty()) input else input + "\n\n## 이전 응답의 문제\n" + problems.joinToString("\n") { "- $it" }
            val text = openAiChatClient.complete(instructions, retryInput, SCHEMA_NAME, schema)

            when (val result = validate(text, condition.durationType, candidates)) {
                is Validation.Success -> {
                    log.info {
                        "planner course: durationType=${condition.durationType} candidates=${candidates.size} " +
                            "stops=${result.draft.stops.size} retried=${attempt > 1}"
                    }
                    return result.draft
                }
                is Validation.Failure -> {
                    problems = result.problems
                    log.warn { "planner course: 응답 검증 실패 attempt=$attempt problems=$problems" }
                }
            }
        }
        throw CourseGenerationException("코스 응답 검증 실패: ${problems.joinToString("; ")}")
    }

    private sealed interface Validation {
        data class Success(
            val draft: CourseDraft,
        ) : Validation

        data class Failure(
            val problems: List<String>,
        ) : Validation
    }

    /** LLM 응답(strict 스키마) 그대로. 스키마가 막아도 방어적으로 전부 nullable. */
    private data class CourseResponse(
        val title: String? = null,
        val summary: String? = null,
        val stops: List<StopResponse>? = null,
    )

    private data class StopResponse(
        val popupId: Long? = null,
        val stayMin: Int? = null,
        val reason: String? = null,
    )

    private fun validate(
        text: String,
        durationType: DurationType,
        candidates: List<CandidatePopup>,
    ): Validation {
        val response =
            try {
                jsonMapper.readValue(text, CourseResponse::class.java)
            } catch (e: JacksonException) {
                log.debug { "planner course: JSON 파싱 실패 ${e.message}" }
                return Validation.Failure(listOf("JSON 파싱 실패. 스키마에 맞는 JSON 만 응답할 것"))
            }

        val problems = mutableListOf<String>()
        val popups = candidates.associateBy { it.popup.id }
        val seen = mutableSetOf<Long>()
        val stops =
            response.stops.orEmpty().filter { stop ->
                when {
                    stop.popupId == null || stop.popupId !in popups -> {
                        problems += "후보에 없는 popupId ${stop.popupId}"
                        false
                    }
                    !seen.add(stop.popupId) -> {
                        problems += "중복된 popupId ${stop.popupId}"
                        false
                    }
                    else -> true
                }
            }

        if (stops.size < durationType.minStops) {
            problems += "방문 개수 ${stops.size}곳은 최소 ${durationType.minStops}곳 미만"
        }
        val title = response.title.trimmed()?.take(MAX_TITLE_LENGTH)
        val summary = response.summary.trimmed()?.take(MAX_SUMMARY_LENGTH)
        if (title == null) problems += "title 이 비었음"
        if (summary == null) problems += "summary 가 비었음"
        val kept = stops.take(durationType.maxStops)
        kept.filter { it.reason.trimmed() == null }.forEach { problems += "popupId ${it.popupId} 의 reason 이 비었음" }

        // 버린 id 만 있고 나머지로 충분하면 성공. 그 외 문제가 하나라도 있으면 실패.
        val fatal = stops.size < durationType.minStops || title == null || summary == null || kept.any { it.reason.trimmed() == null }
        if (fatal) return Validation.Failure(problems)
        if (problems.isNotEmpty()) log.warn { "planner course: 응답 일부 버림 $problems" }

        val stayMins = fitStayMins(kept.map { it.stayMin ?: MIN_STAY_MIN }, durationType)
        return Validation.Success(
            CourseDraft(
                title = title,
                summary = summary,
                stops =
                    kept.zip(stayMins).map { (stop, stayMin) ->
                        CourseStop(
                            popup = popups.getValue(stop.popupId).popup,
                            stayMin = stayMin,
                            reason = stop.reason.trimmed()!!.take(MAX_REASON_LENGTH),
                        )
                    },
            ),
        )
    }

    /**
     * 20~120 clamp 후 10분 단위 반올림. 합계가 예산 × 0.7 을 넘으면 비율대로 줄인다
     * (각각 10분 단위 내림, 최소 20 — 반올림하면 다시 넘을 수 있어 내림).
     */
    private fun fitStayMins(
        raw: List<Int>,
        durationType: DurationType,
    ): List<Int> {
        val rounded =
            raw.map { stayMin ->
                val clamped = stayMin.coerceIn(MIN_STAY_MIN, MAX_STAY_MIN)
                ((clamped + STAY_UNIT_MIN / 2) / STAY_UNIT_MIN) * STAY_UNIT_MIN
            }
        val limit = durationType.budgetMin * STAY_BUDGET_RATIO
        val total = rounded.sum()
        if (total <= limit) return rounded

        val ratio = limit / total
        return rounded.map { stayMin -> ((stayMin * ratio).toInt() / STAY_UNIT_MIN * STAY_UNIT_MIN).coerceAtLeast(MIN_STAY_MIN) }
    }

    private fun String?.trimmed() = this?.trim()?.takeIf { it.isNotEmpty() }
}
