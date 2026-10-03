package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.config.properties.CollectionProperties
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityAgentClient
import com.poppick.poppick.feature.popup.dataaccess.client.perplexity.PerplexityClientException
import com.poppick.poppick.feature.popup.dataaccess.client.web.ImageProbeClient
import com.poppick.poppick.feature.popup.dataaccess.client.web.OgImageClient
import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.domain.ImagePrompt
import com.poppick.poppick.feature.popup.domain.PerplexityUsage
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.concurrent.Executor
import java.util.concurrent.atomic.DoubleAdder

private val log = KotlinLogging.logger { }

/**
 * 보강을 마친 노출 후보 팝업의 대표 이미지 URL 을 모은다(파일은 내려받지 않고 URL 만 저장).
 * 1차: source_urls 중 허용 출처 페이지의 og:image. 페이지 제목에 팝업의 brand · title 이 들어 있을 때만 채택한다.
 * 2차: 1차에서 1장도 못 얻었으면 Perplexity image_search 로 모델이 고른 1장.
 *      모델이 준 주소는 실제 검색 결과(candidates)에 글자 그대로 있고 이미지로 확인될 때만 저장한다(대체 후보로 바꾸지 않는다).
 * 모든 후보는 Content-Type 이 image/… 인지 확인한 뒤 저장하며, 못 찾아도 image_checked_at 은 갱신한다.
 * kakao 수집 풀을 같이 쓴다(collect 단계가 끝난 뒤 실행되므로 겹치지 않는다). Perplexity 속도 제한(RateLimiter)은 보강과 공유한다.
 */
@Component
class PopupImageCollector(
    private val popupReader: PopupReader,
    private val popupWriter: PopupWriter,
    private val ogImageClient: OgImageClient,
    private val imageProbeClient: ImageProbeClient,
    private val perplexityAgentClient: PerplexityAgentClient,
    private val rateLimiter: RateLimiter,
    private val collectionProperties: CollectionProperties,
    @Qualifier("kakaoCollectExecutor")
    private val executor: Executor,
) {
    companion object {
        /** 이보다 짧은 이름(예: 한 글자 브랜드)은 아무 제목에나 걸려서 관련성 판단에 쓰지 않는다. */
        private const val MIN_NAME_LENGTH = 2
        private val WHITESPACE = Regex("\\s+")
    }

    enum class Source { OG, MODEL, NONE }

    /** 팝업 1건 결과. imageUrls 가 비면 못 찾은 것. rejected 는 모델이 준 주소를 확인에 실패해 버렸는지. */
    data class ImageOutcome(
        val imageUrls: List<String>,
        val source: Source,
        val rejected: Boolean = false,
    )

    fun run(today: LocalDate): CollectionReport.Image {
        val startedAt = System.nanoTime()
        val image = collectionProperties.image
        val now = OffsetDateTime.now(KST)
        val targets = popupReader.findImageTargets(today, now.minus(image.retryInterval), image.limit)

        // 응답이 온 Perplexity 요청은 성공 · 실패 무관하게 비용을 합산한다(PopupEnricher 와 같다).
        val cost = DoubleAdder()
        val futures = targets.map { popup -> submit(executor) { collectOne(popup, today, now) { cost.add(it.costUsd) } } }
        val (results, timedOut) = awaitStage("image", futures, image.timeout)

        results.zip(targets).forEach { (result, popup) ->
            result.onFailure { log.warn { "image: 실패 popupId=${popup.id} ${it.javaClass.simpleName}: ${it.message}" } }
        }

        val outcomes = results.mapNotNull { it.getOrNull() }
        return CollectionReport
            .Image(
                targets = targets.size,
                found = outcomes.count { it.source != Source.NONE },
                notFound = outcomes.count { it.source == Source.NONE },
                failed = results.count { it.isFailure },
                fromOg = outcomes.count { it.source == Source.OG },
                fromModel = outcomes.count { it.source == Source.MODEL },
                rejected = outcomes.count { it.rejected },
                costUsd = cost.sum(),
                timedOut = timedOut,
                elapsed = Duration.ofNanos(System.nanoTime() - startedAt),
            ).also { log.info { it.summary() } }
    }

    /**
     * 팝업 1건. Perplexity 호출 · 저장 실패는 예외로 나가며 image_checked_at 도 갱신하지 않는다(다음 실행에 재시도).
     * onUsage 는 Perplexity 응답을 받은 직후(실패 응답 포함) 호출된다.
     */
    fun collectOne(
        popup: Popup,
        today: LocalDate,
        checkedAt: OffsetDateTime,
        onUsage: (PerplexityUsage) -> Unit = {},
    ): ImageOutcome {
        val popupId = requireNotNull(popup.id) { "저장되지 않은 팝업" }
        val fromOg = ogImages(popup, collectionProperties.image.maxPerPopup)
        val outcome =
            if (fromOg.isNotEmpty()) {
                ImageOutcome(fromOg, Source.OG)
            } else {
                modelImage(popup, today, onUsage)
            }

        popupWriter.updateImages(popupId, outcome.imageUrls, checkedAt)
        log.debug { "image: popupId=$popupId source=${outcome.source} count=${outcome.imageUrls.size}" }
        return outcome
    }

    private fun modelImage(
        popup: Popup,
        today: LocalDate,
        onUsage: (PerplexityUsage) -> Unit,
    ): ImageOutcome {
        rateLimiter.acquire()
        val result =
            try {
                perplexityAgentClient.findImage(ImagePrompt.build(popup, today))
            } catch (e: PerplexityClientException) {
                e.usage?.let(onUsage)
                e.incompleteReason?.let { log.warn { "image: 응답 잘림 popupId=${popup.id} reason=$it" } }
                throw e
            }
        onUsage(result.usage)

        val imageUrl = result.imageUrl ?: return ImageOutcome(emptyList(), Source.NONE)
        val reason =
            when {
                imageUrl !in result.candidates -> "not-in-results"
                !imageProbeClient.isImage(imageUrl) -> "probe-failed"
                else -> return ImageOutcome(listOf(imageUrl), Source.MODEL)
            }
        log.debug { "image: 모델 이미지 버림 popupId=${popup.id} reason=$reason url=$imageUrl candidates=${result.candidates.size}" }
        return ImageOutcome(emptyList(), Source.NONE, rejected = true)
    }

    private fun ogImages(
        popup: Popup,
        max: Int,
    ): List<String> {
        val names = listOfNotNull(popup.brand, popup.title).map { it.normalize() }.filter { it.length >= MIN_NAME_LENGTH }
        val images = mutableListOf<String>()
        for (sourceUrl in popup.sourceUrls.orEmpty().filter { ogImageClient.isAllowed(it) }) {
            if (images.size >= max) break
            val page =
                runCatching { ogImageClient.fetch(sourceUrl) }
                    .onFailure { log.debug { "image: 페이지 조회 실패 popupId=${popup.id} url=$sourceUrl ${it.javaClass.simpleName}" } }
                    .getOrNull() ?: continue
            val imageUrl = page.imageUrl ?: continue
            val title = page.title?.normalize()
            if (title == null || names.none { title.contains(it) }) {
                log.debug { "image: 제목 불일치로 제외 popupId=${popup.id} url=$sourceUrl title=${page.title}" }
                continue
            }
            if (imageUrl !in images && imageProbeClient.isImage(imageUrl)) images += imageUrl
        }
        return images
    }

    private fun String.normalize() = replace(WHITESPACE, "").lowercase()
}
