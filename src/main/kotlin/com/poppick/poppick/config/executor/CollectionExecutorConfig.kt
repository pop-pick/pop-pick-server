package com.poppick.poppick.config.executor

import com.poppick.poppick.config.properties.CollectionProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class CollectionExecutorConfig(
    private val collectionProperties: CollectionProperties,
) {
    @Bean
    fun kakaoCollectExecutor() = threadPool(collectionProperties.kakao.threads, "kakao-collect-")

    @Bean
    fun perplexityEnrichExecutor() = threadPool(collectionProperties.perplexity.threads, "pplx-enrich-")

    // queueCapacity 기본값(Integer.MAX_VALUE) = 무제한 큐. core=max 고정 크기 풀.
    private fun threadPool(
        threads: Int,
        threadNamePrefix: String,
    ) = ThreadPoolTaskExecutor().apply {
        corePoolSize = threads
        maxPoolSize = threads
        setThreadNamePrefix(threadNamePrefix)
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(60)
    }
}
