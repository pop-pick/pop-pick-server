package com.poppick.poppick.feature.planner.presentation.scheduler

import com.poppick.poppick.config.properties.PlannerProperties
import com.poppick.poppick.feature.planner.implement.PlannerWriter
import com.poppick.poppick.global.util.KST
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

private val log = KotlinLogging.logger { }

/**
 * 만료 DRAFT 정리. 생성 시 교체(PlannerWriter.saveDraft)로 대부분 지워지고, 다시 오지 않는 회원의 DRAFT 만 여기서 지운다.
 * 단일 인스턴스 전제라 분산 락은 두지 않는다(다중 인스턴스 배포 시 락 필요). 삭제는 bulk 한 번이라 중복 실행돼도 결과는 같다.
 */
@Component
class DraftPurger(
    private val plannerWriter: PlannerWriter,
    private val plannerProperties: PlannerProperties,
    private val clock: Clock = Clock.system(KST),
) {
    @Scheduled(cron = $$"${planner.draft-purge-cron:0 15 * * * *}", zone = "Asia/Seoul")
    fun purge() {
        val olderThan = OffsetDateTime.now(clock).minusHours(plannerProperties.draftTtlHours)
        try {
            val count = plannerWriter.purgeDrafts(olderThan)
            if (count > 0) {
                log.info { "planner drafts purged: count=$count olderThan=$olderThan" }
            } else {
                log.debug { "planner drafts purged: count=0 olderThan=$olderThan" }
            }
        } catch (e: Exception) {
            // 스케줄러 스레드가 죽지 않게 삼킨다. 다음 주기에 다시 시도한다.
            log.error(e) { "planner draft purge 실패 olderThan=$olderThan" }
        }
    }
}
