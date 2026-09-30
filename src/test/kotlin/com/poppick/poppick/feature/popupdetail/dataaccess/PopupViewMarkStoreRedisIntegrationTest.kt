package com.poppick.poppick.feature.popupdetail.dataaccess

import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 로컬 Redis 로 조회 메모의 SET NX EX 동작을 확인한다(조회수 · 공유 DB 와 무관).
 * POPUP_VIEW_TEST_REDIS_HOST 가 설정된 경우에만 실행된다. 예: localhost (docker-compose-local.yml 의 Redis)
 * 테스트가 만든 key 는 끝나면 지운다.
 */
@EnabledIfEnvironmentVariable(named = "POPUP_VIEW_TEST_REDIS_HOST", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopupViewMarkStoreRedisIntegrationTest {
    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var store: PopupViewMarkStore

    /** 실제 팝업 id 와 겹치지 않도록 음수 id 를 쓴다. */
    private val popupId = -Random.nextLong(1, Long.MAX_VALUE)

    @BeforeAll
    fun setUp() {
        val host = System.getenv("POPUP_VIEW_TEST_REDIS_HOST")
        val port = System.getenv("POPUP_VIEW_TEST_REDIS_PORT")?.toIntOrNull() ?: 6379
        connectionFactory = LettuceConnectionFactory(RedisStandaloneConfiguration(host, port)).apply { afterPropertiesSet() }
        redisTemplate = StringRedisTemplate(connectionFactory)
        store = PopupViewMarkStore(redisTemplate)
    }

    @AfterAll
    fun tearDown() {
        redisTemplate.keys("popup:view:$popupId:*").takeIf { it.isNotEmpty() }?.let { redisTemplate.delete(it) }
        connectionFactory.destroy()
    }

    @Test
    fun `첫 조회만 true 이고 같은 조회자의 재조회는 false, 다른 조회자는 true`() {
        val viewer = PopupViewer.member("it-member-a")

        store.markIfAbsent(popupId, viewer) shouldBe true
        store.markIfAbsent(popupId, viewer) shouldBe false
        store.markIfAbsent(popupId, PopupViewer.member("it-member-b")) shouldBe true
    }

    @Test
    fun `메모는 값 1 과 10분 이하 TTL 로 저장된다`() {
        val viewer = PopupViewer.anonymous("203.0.113.7", null, "172.18.0.5", "it-ua")
        store.markIfAbsent(popupId, viewer)

        val key = "popup:view:$popupId:${viewer.key}"
        redisTemplate.opsForValue().get(key) shouldBe "1"
        val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
        ttl shouldBeGreaterThan 0
        ttl shouldBeLessThanOrEqual 600
    }

    @Test
    fun `같은 조회자의 동시 요청 50건 중 정확히 1건만 true`() {
        val viewer = PopupViewer.member("it-member-concurrent")
        val threads = 50
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val futures = List(threads) { pool.submit<Boolean> { start.await().let { store.markIfAbsent(popupId, viewer) } } }
            start.countDown()

            futures.count { it.get(10, TimeUnit.SECONDS) } shouldBe 1
        } finally {
            pool.shutdownNow()
        }
    }
}
