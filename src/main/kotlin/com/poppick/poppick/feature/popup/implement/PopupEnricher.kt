package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.config.properties.CollectionProperties
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityAgentClient
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityClientException
import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.domain.EnrichmentPrompt
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SearchRecency
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private val log = KotlinLogging.logger { }

/**
 * 보강 대상을 병렬로 Perplexity 에 조회해 병합 · 저장한다.
 * 트랜잭션은 PopupWriter(별도 빈)에서 태스크 스레드마다 열린다. 여기에는 @Transactional 을 두지 않는다.
 */
@Component
class PopupEnricher(
    private val popupReader: PopupReader,
    private val popupWriter: PopupWriter,
    private val interestCategoryReader: InterestCategoryReader,
    private val perplexityAgentClient: PerplexityAgentClient,
    private val popupEnrichmentMerger: PopupEnrichmentMerger,
    private val rateLimiter: RateLimiter,
    private val collectionProperties: CollectionProperties,
    @Qualifier("perplexityEnrichExecutor")
    private val executor: Executor,
) {
    companion object {
        /** 연속으로 이만큼 4xx 가 나면 요청 설정 오류로 보고 남은 대상을 제출하지 않는다. */
        private const val CONSECUTIVE_CLIENT_ERROR_LIMIT = 5
    }

    fun enrich(today: LocalDate): CollectionReport.Enrich {
        val startedAt = System.nanoTime()
        val deadline = startedAt + collectionProperties.enrichTimeout.toNanos()
        val retryBefore = OffsetDateTime.now(KST).minus(collectionProperties.enrichRetryInterval)
        val targetIds =
            popupReader.findEnrichTargetIds(
                collectionProperties.enrichRetryLimit,
                retryBefore,
                collectionProperties.enrichLimit,
            )
        val categories = interestCategoryReader.findAll().associate { it.category to it.id }

        // 동시 실행 수만큼만 제출해 두고, 제출 직전마다 4xx 연속 횟수를 검사한다(실행 중 태스크는 그대로 끝낸다).
        val inFlight = Semaphore(collectionProperties.perplexity.threads)
        val consecutiveClientErrors = AtomicInteger()
        val futures = mutableListOf<CompletableFuture<Result<EnrichOutcome>>>()
        var submitTimedOut = false
        for (id in targetIds) {
            if (!inFlight.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS)) {
                submitTimedOut = true
                log.warn { "enrich: 제출 중 타임아웃, 나머지 ${targetIds.size - futures.size}건 건너뜀" }
                break
            }
            if (consecutiveClientErrors.get() >= CONSECUTIVE_CLIENT_ERROR_LIMIT) {
                inFlight.release()
                log.warn { "enrich: 4xx 연속 ${CONSECUTIVE_CLIENT_ERROR_LIMIT}건, 나머지 ${targetIds.size - futures.size}건 건너뜀" }
                break
            }
            futures +=
                submit(executor) {
                    try {
                        enrichOne(id, today, categories).also { consecutiveClientErrors.set(0) }
                    } catch (e: PerplexityClientException) {
                        if (e.isClientError) consecutiveClientErrors.incrementAndGet()
                        throw e
                    } finally {
                        inFlight.release()
                    }
                }
        }
        val submittedIds = targetIds.take(futures.size)
        val (results, timedOut) = awaitStage("enrich", futures, Duration.ofNanos(remaining(deadline)))

        results.zip(submittedIds).forEach { (result, id) ->
            result.onFailure { log.warn { "enrich: 실패 popupId=$id ${it.javaClass.simpleName}: ${it.message}" } }
        }

        val outcomes = results.mapNotNull { it.getOrNull() }
        val saved = outcomes.map { it.popup }
        val ended = saved.filter { it.endDate?.isBefore(today) == true }
        return CollectionReport
            .Enrich(
                targets = targetIds.size,
                enriched = outcomes.count { it.found },
                notFound = outcomes.count { !it.found },
                failed = results.count { it.isFailure },
                skipped = targetIds.size - futures.size,
                active = (saved - ended.toSet()).count { it.hasCoreFields() },
                ended = ended.size,
                incomplete = (saved - ended.toSet()).count { !it.hasCoreFields() },
                timedOut = timedOut || submitTimedOut,
                elapsed = Duration.ofNanos(System.nanoTime() - startedAt),
            ).also { log.info { it.summary() } }
    }

    private fun remaining(deadline: Long) = (deadline - System.nanoTime()).coerceAtLeast(0)

    /** 팝업 1건 보강 결과. found 는 응답이 이 장소의 팝업 정보를 담고 있었는지(요약 로그용). */
    data class EnrichOutcome(
        val popup: Popup,
        val found: Boolean,
    )

    /** 팝업 1건 보강. 호출 · 파싱 실패 시 예외가 나며 DB 는 건드리지 않는다. */
    fun enrichOne(
        popupId: Long,
        today: LocalDate,
        categories: Map<String, Int>,
    ): EnrichOutcome {
        rateLimiter.acquire()
        val popup = popupReader.findById(popupId)
        val result = perplexityAgentClient.enrich(EnrichmentPrompt.build(popup, today), SearchRecency.of(popup))
        val merged = popupEnrichmentMerger.merge(popup, result, categories, OffsetDateTime.now(KST))
        return EnrichOutcome(popupWriter.save(merged), result.enrichment.describesPlace())
    }
}
