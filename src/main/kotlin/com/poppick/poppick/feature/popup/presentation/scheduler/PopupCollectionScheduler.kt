package com.poppick.poppick.feature.popup.presentation.scheduler

import com.poppick.poppick.feature.popup.business.PopupCollectionService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

private val log = KotlinLogging.logger { }

/** 팝업 수집 배치 트리거. 단일 인스턴스 전제라 중복 실행 방지는 프로세스 내 플래그로 충분하다. */
@Component
class PopupCollectionScheduler(
    private val popupCollectionService: PopupCollectionService,
) {
    private val running = AtomicBoolean(false)

    @Scheduled(cron = $$"${collection.cron}", zone = "Asia/Seoul")
    fun collect() {
        if (!running.compareAndSet(false, true)) {
            log.warn { "popup collection 이 이미 실행 중이라 건너뜀" }
            return
        }
        try {
            popupCollectionService.run()
        } catch (e: Exception) {
            log.error(e) { "popup collection 실패" }
        } finally {
            running.set(false)
        }
    }
}
