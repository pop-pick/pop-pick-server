package com.poppick.poppick.feature.popup.business

import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.implement.KakaoPopupCollector
import com.poppick.poppick.feature.popup.implement.PopupEnricher
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate

private val log = KotlinLogging.logger { }

@Service
class PopupCollectionService(
    private val kakaoPopupCollector: KakaoPopupCollector,
    private val popupEnricher: PopupEnricher,
) {
    /** collect → enrich. 수집이 실패해도 보강은 진행한다. 만료는 저장하지 않고 조회 시 end_date 로 계산한다. */
    fun run(): CollectionReport {
        val startedAt = System.nanoTime()
        val today = LocalDate.now(KST)

        val collect = stage("collect") { kakaoPopupCollector.collect() }
        val enrich = stage("enrich") { popupEnricher.enrich(today) }

        return CollectionReport(today, collect, enrich, Duration.ofNanos(System.nanoTime() - startedAt))
            .also { log.info { it.summary() } }
    }

    private fun <T> stage(
        name: String,
        block: () -> T,
    ): T? =
        runCatching(block)
            .onFailure { log.warn(it) { "$name: 단계 실패, 다음 단계로 진행" } }
            .getOrNull()
}
