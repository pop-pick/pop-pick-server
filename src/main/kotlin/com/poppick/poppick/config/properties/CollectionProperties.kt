package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 팝업 수집 배치(collect → enrich) 설정. 값은 전부 yml 의 collection.* 에서 받는다. */
@ConfigurationProperties("collection")
data class CollectionProperties(
    /** 실행 주기(Asia/Seoul 기준 cron). 스케줄러는 @Scheduled 의 플레이스홀더로 같은 키를 읽는다. */
    val cron: String,
    /** 한 번 실행에서 보강할 최대 팝업 수. */
    val enrichLimit: Int,
    /** 총 시도 횟수(최초 포함). 핵심 필드를 채우지 못한 채 이 횟수에 도달하면 재보강 대상에서 빠진다(팝업은 그대로 노출). */
    val enrichRetryLimit: Int,
    /** 재보강 간격. 마지막 보강 시각에서 이만큼 지나야 다시 보강한다. */
    val enrichRetryInterval: Duration,
    val collectTimeout: Duration,
    val enrichTimeout: Duration,
    val kakao: Pool,
    val perplexity: Pool,
) {
    data class Pool(
        /** 스레드 수. perplexity 는 동시에 제출해 둘 최대 태스크 수로도 쓴다. */
        val threads: Int,
    )
}
