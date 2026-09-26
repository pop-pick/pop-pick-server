package com.poppick.poppick.feature.popup

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** 지정한 로거의 로그를 모은다. 테스트에서 WARN 이 찍혔는지 확인할 때 쓴다. */
class LogCapture(
    loggerName: String,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(loggerName) as Logger
    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    init {
        logger.addAppender(appender)
    }

    fun messages(): List<String> = appender.list.map { it.formattedMessage }

    override fun close() {
        logger.detachAppender(appender)
    }
}
