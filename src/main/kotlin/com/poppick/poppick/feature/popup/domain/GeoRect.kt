package com.poppick.poppick.feature.popup.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** 카카오 키워드 검색의 rect 파라미터(좌하단 경도,위도,우상단 경도,위도). x = 경도, y = 위도. */
data class GeoRect(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    init {
        require(minX < maxX && minY < maxY) { "rect 좌하단이 우상단보다 작아야 한다: ${toParam()}" }
    }

    companion object {
        /** 카카오 rect 는 소수 6자리면 충분하다(약 0.1m). 분할 중점의 부동소수 꼬리를 자른다. */
        private const val PARAM_SCALE = 6

        /** "126.764,37.413,127.184,37.715" 형식. */
        fun parse(text: String): GeoRect {
            val values = text.split(",").map { it.trim().toDouble() }
            require(values.size == 4) { "rect 는 숫자 4개여야 한다: $text" }
            return GeoRect(values[0], values[1], values[2], values[3])
        }
    }

    /** 4등분(좌하 · 우하 · 좌상 · 우상). */
    fun quarters(): List<GeoRect> {
        val midX = (minX + maxX) / 2
        val midY = (minY + maxY) / 2
        return listOf(
            GeoRect(minX, minY, midX, midY),
            GeoRect(midX, minY, maxX, midY),
            GeoRect(minX, midY, midX, maxY),
            GeoRect(midX, midY, maxX, maxY),
        )
    }

    fun toParam() = listOf(minX, minY, maxX, maxY).joinToString(",") { it.format() }

    private fun Double.format() =
        BigDecimal(this)
            .setScale(PARAM_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
}
