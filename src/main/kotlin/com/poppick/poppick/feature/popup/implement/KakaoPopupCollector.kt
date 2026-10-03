package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.config.properties.CollectionProperties
import com.poppick.poppick.feature.popup.dataaccess.client.kakao.KakaoMapClient
import com.poppick.poppick.feature.popup.dataaccess.repository.SearchKeywordRepository
import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter.UpsertResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.Executor

private val log = KotlinLogging.logger { }

@Component
class KakaoPopupCollector(
    private val searchKeywordRepository: SearchKeywordRepository,
    private val kakaoMapClient: KakaoMapClient,
    private val popupWriter: PopupWriter,
    private val collectionProperties: CollectionProperties,
    @Qualifier("kakaoCollectExecutor")
    private val executor: Executor,
) {
    /**
     * 활성 검색어로 카카오맵을 병렬 검색 → 서울 필터 → place id dedupe → 호출 스레드에서 순차 upsert.
     * upsert 를 병렬로 돌리면 (source, external_id) UNIQUE 충돌이 나므로 저장은 반드시 순차로 한다.
     * 이번에 발견한 장소는 신규 · 기존 모두 last_seen_at = now 로 기록한다.
     */
    fun collect(now: OffsetDateTime): CollectionReport.Collect {
        val startedAt = System.nanoTime()
        val keywords =
            searchKeywordRepository
                .findAllByTargetSourceAndIsActiveTrueOrderByIdAsc(SourceType.KAKAO_MAP)
                .map { it.toDomain() }

        val futures = keywords.map { keyword -> submit(executor) { kakaoMapClient.searchAll(keyword.keyword) } }
        val (results, timedOut) = awaitStage("collect", futures, collectionProperties.collectTimeout)

        results.zip(keywords).forEach { (result, keyword) ->
            result.onFailure { log.warn { "collect: 검색 실패 keyword='${keyword.keyword}' ${it.javaClass.simpleName}: ${it.message}" } }
        }

        val searched = results.mapNotNull { it.getOrNull() }
        val fetched = searched.flatMap { it.places }
        val seoul = fetched.filter { it.isInSeoul() }
        val unique = seoul.distinctBy { it.id }

        val upserts =
            unique.map { place ->
                runCatching { popupWriter.upsertFromKakao(place, now) }
                    .onFailure { log.warn { "collect: 저장 실패 placeId=${place.id} ${it.javaClass.simpleName}: ${it.message}" } }
                    .getOrNull()
            }

        return CollectionReport
            .Collect(
                keywords = keywords.size,
                failedKeywords = results.count { it.isFailure },
                partialKeywords = searched.count { it.partial },
                truncatedKeywords = searched.count { it.truncated },
                fetched = fetched.size,
                seoul = seoul.size,
                unique = unique.size,
                created = upserts.count { it == UpsertResult.CREATED },
                updated = upserts.count { it == UpsertResult.UPDATED },
                failed = upserts.count { it == null },
                timedOut = timedOut,
                elapsed = Duration.ofNanos(System.nanoTime() - startedAt),
            ).also { log.info { it.summary() } }
    }
}
