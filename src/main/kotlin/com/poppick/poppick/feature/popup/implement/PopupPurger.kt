package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.domain.CollectionReport
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

private val log = KotlinLogging.logger { }

/**
 * 종료일이 없거나 지난 팝업(end_date IS NULL OR end_date < today)과 그 임베딩을 삭제한다.
 * 시작일 · 카테고리 · 보강 여부는 보지 않는다. 트랜잭션은 PopupWriter.deleteAll 에서 한 번 열린다.
 */
@Component
class PopupPurger(
    private val popupReader: PopupReader,
    private val popupWriter: PopupWriter,
) {
    fun run(today: LocalDate): CollectionReport.Purge {
        val startedAt = System.nanoTime()

        val ids = popupReader.findExpiredPopupIds(today)
        val deleted =
            if (ids.isEmpty()) {
                PopupWriter.DeleteResult(popups = 0, embeddings = 0)
            } else {
                log.debug { "purge: 삭제 대상 popupIds=$ids" }
                popupWriter.deleteAll(ids)
            }

        return CollectionReport
            .Purge(
                popups = deleted.popups,
                embeddings = deleted.embeddings,
                elapsed = Duration.ofNanos(System.nanoTime() - startedAt),
            ).also { log.info { it.summary() } }
    }
}
