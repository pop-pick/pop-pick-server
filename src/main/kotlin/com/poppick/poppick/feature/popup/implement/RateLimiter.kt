package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.config.properties.PerplexityProperties
import org.springframework.stereotype.Component
import java.util.concurrent.Semaphore

/** 고정 간격 속도 제한. acquire() 는 직전 허가로부터 1/requestsPerSecond 초가 지날 때까지 블록한다. */
@Component
class RateLimiter(
    properties: PerplexityProperties,
) {
    private val intervalNanos = (1_000_000_000L / properties.requestsPerSecond).toLong()
    private val permit = Semaphore(1, true)
    private var nextAllowedAt = 0L

    fun acquire() {
        permit.acquire()
        try {
            val now = System.nanoTime()
            val waitNanos = nextAllowedAt - now
            if (waitNanos > 0) Thread.sleep(waitNanos / 1_000_000, (waitNanos % 1_000_000).toInt())
            nextAllowedAt = maxOf(now, nextAllowedAt) + intervalNanos
        } finally {
            permit.release()
        }
    }
}
