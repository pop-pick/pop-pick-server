package com.poppick.poppick.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 팝업 수집 배치(collect → enrich → image → embed) 설정. 값은 전부 yml 의 collection.* 에서 받는다. */
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
    /** 진행 중(end_date >= 오늘) 팝업의 갱신 간격. 마지막 보강 시각에서 이만큼 지나면 핵심 필드가 차 있어도 다시 보강한다. */
    val refreshInterval: Duration,
    /** 종료일이 오늘부터 이 일수 이내면 종료 임박으로 보고 refreshImminentInterval 을 쓴다. */
    val refreshImminentDays: Long,
    /** 종료 임박 팝업의 갱신 간격. */
    val refreshImminentInterval: Duration,
    val collectTimeout: Duration,
    val enrichTimeout: Duration,
    val embedTimeout: Duration,
    val kakao: Pool,
    val perplexity: Pool,
    val image: Image,
) {
    data class Pool(
        /** 스레드 수. perplexity 는 동시에 제출해 둘 최대 태스크 수로도 쓴다. */
        val threads: Int,
    )

    /** 이미지 URL 수집 단계(image). 스레드는 kakao 풀을 같이 쓴다. */
    data class Image(
        /** 한 번 실행에서 처리할 최대 팝업 수. */
        val limit: Int,
        /** 이미지를 못 찾은 팝업을 다시 시도하기까지의 간격(image_checked_at 기준). */
        val retryInterval: Duration,
        val timeout: Duration,
        /** 팝업 1건에 저장할 최대 이미지 수. */
        val maxPerPopup: Int,
        /** og:image 를 읽어도 되는 출처 페이지. source_urls 중 여기에 맞는 https URL 만 요청한다. */
        val allowedSources: List<AllowedSource>,
    )

    data class AllowedSource(
        /** 정확히 일치해야 하는 host(대소문자 무시). */
        val host: String,
        /** 경로 접두사. 제한이 없으면 "/". 목록 페이지(사이트 로고만 나온다)를 거르는 용도. */
        val pathPrefix: String,
    )
}
