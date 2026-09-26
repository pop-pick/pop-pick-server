package com.poppick.poppick.global.util

import java.time.Duration
import java.time.ZoneId

val KST: ZoneId = ZoneId.of("Asia/Seoul")

/** 로그용 경과 시간 표기. 예: 12.3s */
fun Duration.toSecondsText() = "%.1fs".format(toMillis() / 1000.0)
