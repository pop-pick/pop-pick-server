package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.config.properties.CollectionProperties
import com.poppick.poppick.config.properties.OpenAiProperties
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiEmbeddingClient
import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import com.poppick.poppick.feature.popup.domain.PopupProfileText
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

private val log = KotlinLogging.logger { }

/**
 * 노출 후보 팝업의 프로필 텍스트를 임베딩해 저장한다. 원문 해시가 같으면 다시 임베딩하지 않는다.
 * 대상이 수십 건 규모라 배치(batchSize)를 순차 호출한다. 트랜잭션은 PopupEmbeddingWriter 에서 배치마다 열린다.
 */
@Component
class PopupEmbedder(
    private val popupReader: PopupReader,
    private val popupEmbeddingReader: PopupEmbeddingReader,
    private val popupEmbeddingWriter: PopupEmbeddingWriter,
    private val interestCategoryReader: InterestCategoryReader,
    private val openAiEmbeddingClient: OpenAiEmbeddingClient,
    private val openAiProperties: OpenAiProperties,
    private val collectionProperties: CollectionProperties,
) {
    /** 임베딩할 원문 1건. */
    private data class Content(
        val popupId: Long,
        val text: String,
        val hash: String,
    )

    /**
     * 시간 제한은 배치 시작 전에 검사한다(진행 중인 요청은 끊지 않는다).
     * 넘으면 남은 배치는 skipped 로 세고 timedOut=true.
     */
    fun run(today: LocalDate): CollectionReport.Embed {
        val startedAt = System.nanoTime()
        val deadline = startedAt + collectionProperties.embedTimeout.toNanos()
        val model = openAiProperties.embeddingModel

        val targets = popupReader.findEmbedTargets(today)
        val categories = interestCategoryReader.findAll().associate { it.id to it.category }
        val contents =
            targets.mapNotNull { popup ->
                val id = popup.id ?: return@mapNotNull null
                val text = PopupProfileText.build(popup, popup.interestCategoryId?.let { categories[it] })
                Content(id, text, PopupProfileText.sha256(text))
            }

        val existingHashes =
            popupEmbeddingReader
                .findAll(contents.map { it.popupId }, PopupEmbedding.KIND_PROFILE, model)
                .associate { it.popupId to it.contentHash }
        val pending = contents.filter { existingHashes[it.popupId] != it.hash }

        var embedded = 0
        var failed = 0
        var timeoutSkipped = 0
        var promptTokens = 0
        var costUsd = 0.0
        var timedOut = false
        val batches = pending.chunked(openAiProperties.batchSize)
        for ((index, batch) in batches.withIndex()) {
            if (System.nanoTime() >= deadline) {
                timedOut = true
                timeoutSkipped = batches.drop(index).sumOf { it.size }
                log.warn { "embed: ${collectionProperties.embedTimeout} 타임아웃, 나머지 ${timeoutSkipped}건 건너뜀" }
                break
            }
            try {
                val result = openAiEmbeddingClient.embed(batch.map { it.text })
                // 응답을 받은 건 저장 실패와 무관하게 비용을 합산한다.
                promptTokens += result.promptTokens
                costUsd += result.costUsd
                popupEmbeddingWriter.upsert(
                    batch.zip(result.vectors) { content, vector ->
                        PopupEmbedding(
                            popupId = content.popupId,
                            kind = PopupEmbedding.KIND_PROFILE,
                            model = model,
                            contentText = content.text,
                            contentHash = content.hash,
                            embedding = vector,
                        )
                    },
                )
                embedded += batch.size
            } catch (e: Exception) {
                failed += batch.size
                log.warn { "embed: 배치 실패 popupIds=${batch.map { it.popupId }} ${e.javaClass.simpleName}: ${e.message}" }
            }
        }

        return CollectionReport
            .Embed(
                targets = targets.size,
                embedded = embedded,
                skipped = (contents.size - pending.size) + timeoutSkipped,
                failed = failed,
                promptTokens = promptTokens,
                costUsd = costUsd,
                timedOut = timedOut,
                elapsed = Duration.ofNanos(System.nanoTime() - startedAt),
            ).also { log.info { it.summary() } }
    }
}
