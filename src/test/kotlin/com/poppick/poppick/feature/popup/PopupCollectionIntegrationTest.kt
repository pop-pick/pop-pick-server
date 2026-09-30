package com.poppick.poppick.feature.popup

import com.poppick.poppick.feature.popup.business.PopupCollectionService
import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 실제 카카오 · Perplexity · DB 로 수집 배치를 1회 실행한다(수동 검증용, 과금 발생).
 * local 프로파일(Secrets Manager: KAKAO_API_KEY · PERPLEXITY_API_KEY · DB_*) 이 필요하다.
 * findEnrichTargets 케이스는 실 DB 에 쓰지만 @Transactional 로 롤백된다(favorite_area 1 성수가 있어야 한다).
 */
@Disabled("수동 실행 전용: 외부 API 호출 · 실 DB 쓰기")
@SpringBootTest
@ActiveProfiles("local")
class PopupCollectionIntegrationTest {
    @Autowired
    lateinit var popupCollectionService: PopupCollectionService

    @Autowired
    lateinit var popupRepository: PopupRepository

    private val now = OffsetDateTime.now(KST)

    @Test
    fun run() {
        println(popupCollectionService.run())
    }

    @Test
    @Transactional
    fun `area_id 가 NULL 인 보강 완료 팝업은 재보강 대상이다`() {
        val popup = enrichedPopup("it-area-null", areaId = null)

        enrichTargetIds() shouldContain popup.id
    }

    @Test
    @Transactional
    fun `핵심 필드와 area 가 모두 채워진 팝업은 재보강 대상이 아니다`() {
        val popup = enrichedPopup("it-area-filled", areaId = 1)

        enrichTargetIds() shouldNotContain popup.id
    }

    // 핵심 필드(기간 · 카테고리)가 다 차 있고 retry 한도 안인, 재보강 간격(7일)이 지난 팝업.
    private fun enrichedPopup(
        externalId: String,
        areaId: Int?,
    ) = popupRepository.saveAndFlush(
        PopupEntity(
            source = SourceType.KAKAO_MAP,
            externalId = externalId,
            title = "area 재보강 테스트 $externalId",
            interestCategoryId = 1,
            areaId = areaId,
            startDate = LocalDate.now(KST),
            endDate = LocalDate.now(KST).plusDays(30),
            enrichRetryCount = 0,
            enrichedAt = now.minusDays(8),
        ),
    )

    private fun enrichTargetIds() =
        popupRepository
            .findEnrichTargets(retryLimit = 2, retryBefore = now.minusDays(7), limit = Int.MAX_VALUE)
            .map { it.id }
}
