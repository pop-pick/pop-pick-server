package com.poppick.poppick.feature.popup.business

import com.poppick.poppick.feature.popup.domain.CollectionReport
import com.poppick.poppick.feature.popup.implement.KakaoPopupCollector
import com.poppick.poppick.feature.popup.implement.PopupEmbedder
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
    private val popupEmbedder: PopupEmbedder,
) {
    /**
     * collect → enrich → embed. 앞 단계가 실패해도 다음 단계는 이미 저장된 팝업 기준으로 진행한다.
     * 배치는 팝업을 삭제하지 않는다. 종료 여부는 조회 쪽에서 `end_date` 로 판단한다.
     */
    fun run(): CollectionReport {
        val startedAt = System.nanoTime()
        val today = LocalDate.now(KST)

        val collect = stage("collect") { kakaoPopupCollector.collect() }
        val enrich = stage("enrich") { popupEnricher.enrich(today) }
        val embed = stage("embed") { popupEmbedder.run(today) }

        return CollectionReport(today, collect, enrich, embed, Duration.ofNanos(System.nanoTime() - startedAt))
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
