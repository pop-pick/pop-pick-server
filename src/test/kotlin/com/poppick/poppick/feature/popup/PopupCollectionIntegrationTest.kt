package com.poppick.poppick.feature.popup

import com.poppick.poppick.feature.popup.business.PopupCollectionService
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * 실제 카카오 · Perplexity · DB 로 수집 배치를 1회 실행한다(수동 검증용, 과금 발생).
 * local 프로파일(Secrets Manager: KAKAO_API_KEY · PERPLEXITY_API_KEY · DB_*) 이 필요하다.
 */
@Disabled("수동 실행 전용: 외부 API 호출 · 실 DB 쓰기")
@SpringBootTest
@ActiveProfiles("local")
class PopupCollectionIntegrationTest {
    @Autowired
    lateinit var popupCollectionService: PopupCollectionService

    @Test
    fun run() {
        println(popupCollectionService.run())
    }
}
