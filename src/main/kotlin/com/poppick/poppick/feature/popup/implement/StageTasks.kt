package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.global.util.toSecondsText
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private val log = KotlinLogging.logger { }

/** 태스크 제출. 예외는 밖으로 던지지 않고 Result 로 담아 호출 스레드가 집계한다. */
internal fun <T> submit(
    executor: Executor,
    task: () -> T,
): CompletableFuture<Result<T>> = CompletableFuture.supplyAsync({ runCatching(task) }, executor)

/**
 * 단계의 모든 태스크를 timeout 까지 기다린다. 시간 안에 끝나지 않으면 남은 future 를 cancel 하고 true 를 반환.
 * 취소된(또는 미완료) 태스크의 결과는 failure 로 채운다.
 */
internal fun <T> awaitStage(
    stage: String,
    futures: List<CompletableFuture<Result<T>>>,
    timeout: Duration,
): Pair<List<Result<T>>, Boolean> {
    val timedOut =
        try {
            CompletableFuture
                .allOf(*futures.toTypedArray())
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .join()
            false
        } catch (e: CompletionException) {
            if (e.cause !is TimeoutException) throw e
            val pending = futures.count { it.cancel(true) }
            log.warn { "$stage: ${timeout.toSecondsText()} 타임아웃, 미완료 태스크 ${pending}건 취소" }
            true
        }

    val results =
        futures.map { future ->
            if (future.isDone && !future.isCancelled && !future.isCompletedExceptionally) {
                future.join()
            } else {
                Result.failure(TimeoutException("$stage 타임아웃으로 취소됨"))
            }
        }
    return results to timedOut
}
