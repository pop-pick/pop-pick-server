package com.poppick.poppick.feature.popup

import com.poppick.poppick.config.properties.CollectionProperties
import com.poppick.poppick.config.properties.KakaoMapProperties
import com.poppick.poppick.config.properties.OpenAiProperties
import com.poppick.poppick.config.properties.PerplexityProperties
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Duration

object Fixtures {
    val jsonMapper: JsonMapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    fun read(path: String): String = ClassPathResource("fixtures/$path").getContentAsString(Charsets.UTF_8)

    /** yml(collection.*) 과 같은 값. 테스트마다 필요한 것만 바꿔 쓴다. */
    fun collectionProperties(
        enrichRetryLimit: Int = 2,
        collectTimeout: Duration = Duration.ofMinutes(5),
        enrichTimeout: Duration = Duration.ofMinutes(30),
        embedTimeout: Duration = Duration.ofMinutes(5),
        perplexityThreads: Int = 3,
    ) = CollectionProperties(
        cron = "0 30 5 * * *",
        enrichLimit = 200,
        enrichRetryLimit = enrichRetryLimit,
        enrichRetryInterval = Duration.ofDays(7),
        collectTimeout = collectTimeout,
        enrichTimeout = enrichTimeout,
        embedTimeout = embedTimeout,
        kakao = CollectionProperties.Pool(threads = 4),
        perplexity = CollectionProperties.Pool(threads = perplexityThreads),
    )

    fun kakaoMapProperties(maxPage: Int = 3) =
        KakaoMapProperties(
            baseUrl = "https://dapi.kakao.com",
            seoulRect = "126.764,37.413,127.184,37.715",
            maxPage = maxPage,
            pageDelayMs = 0,
        )

    fun perplexityProperties() =
        PerplexityProperties(
            apiKey = "test-pplx-key",
            baseUrl = "https://api.perplexity.ai",
            model = "openai/gpt-6-luna",
            readTimeoutSeconds = 120,
            requestsPerSecond = 1.0,
        )

    fun openAiProperties(
        embeddingDimensions: Int = 1536,
        batchSize: Int = 32,
    ) = OpenAiProperties(
        apiKey = "test-openai-key",
        baseUrl = "https://api.openai.com",
        embeddingModel = "text-embedding-3-small",
        embeddingDimensions = embeddingDimensions,
        readTimeoutSeconds = 60,
        batchSize = batchSize,
    )
}
