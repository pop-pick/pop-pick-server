package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.config.properties.OpenAiProperties
import com.poppick.poppick.config.properties.PlannerProperties
import com.poppick.poppick.feature.planner.domain.CandidateCondition
import com.poppick.poppick.feature.planner.domain.CandidatePopup
import com.poppick.poppick.feature.planner.domain.CandidateQueryText
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiEmbeddingClient
import com.poppick.poppick.feature.popup.implement.PopupEmbeddingReader
import com.poppick.poppick.feature.popup.implement.PopupReader
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger { }

/**
 * 사용자 취향(요청의 카테고리 · 활동 + 자유 입력)과 비슷한 후보 팝업을 벡터 검색으로 최대 candidateLimit 건 뽑는다.
 * 지역 · 방문일 · 좌표 유무는 SQL 로 거른다. 결과가 비어도 빈 리스트를 돌려주고, 422 판단은 호출 측이 한다.
 */
@Component
class CandidatePopupFinder(
    private val openAiEmbeddingClient: OpenAiEmbeddingClient,
    private val popupEmbeddingReader: PopupEmbeddingReader,
    private val popupReader: PopupReader,
    private val openAiProperties: OpenAiProperties,
    private val plannerProperties: PlannerProperties,
) {
    companion object {
        private const val LOG_QUERY_LENGTH = 60
    }

    fun find(condition: CandidateCondition): List<CandidatePopup> {
        val queryText = CandidateQueryText.build(condition.categories, condition.activities, condition.note)
        val queryVector = queryText?.let { openAiEmbeddingClient.embed(listOf(it)).vectors.single() }

        val hits =
            popupEmbeddingReader.searchSimilar(
                queryVector = queryVector,
                model = openAiProperties.embeddingModel,
                areaId = condition.areaId,
                visitDate = condition.visitDate,
                excludePopupIds = condition.excludePopupIds,
                limit = plannerProperties.candidateLimit,
            )
        val popups = popupReader.findAllByIds(hits.map { it.popupId }).associateBy { it.id }
        val candidates = hits.mapNotNull { hit -> popups[hit.popupId]?.let { CandidatePopup(it, hit.distance) } }

        log.info {
            "planner candidates: areaId=${condition.areaId} visitDate=${condition.visitDate} " +
                "query=${queryText?.replace("\n", " / ")?.take(LOG_QUERY_LENGTH) ?: "none"} hits=${candidates.size}"
        }
        return candidates
    }
}
