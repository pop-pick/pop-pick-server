package com.poppick.poppick.feature.planner.domain

import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 목록 keyset 커서. 정렬 키 전체를 담아야 같은 날 여러 일정이 있어도 빠짐 · 중복이 없다.
 * - UPCOMING · PAST: "{visitDate}T{HH:mm}_{plannerId}" 예) 2026-10-03T14:00_12
 * - CANCELED: "{canceledAtEpochMs}_{plannerId}" 예) 1790000000000_12 (canceled_at 은 ms 단위로 저장한다)
 */
sealed interface PlannerListCursor {
    val plannerId: Long

    data class Visit(
        val visitDate: LocalDate,
        val startTime: LocalTime,
        override val plannerId: Long,
    ) : PlannerListCursor {
        override fun encode() = "${visitDate}T${startTime.format(HH_MM)}_$plannerId"
    }

    data class Canceled(
        val canceledAt: Instant,
        override val plannerId: Long,
    ) : PlannerListCursor {
        override fun encode() = "${canceledAt.toEpochMilli()}_$plannerId"
    }

    fun encode(): String

    companion object {
        private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** 형식이 탭과 맞지 않으면 INVALID_REQUEST. */
        fun parse(
            tab: PlannerListTab,
            raw: String,
        ): PlannerListCursor =
            runCatching {
                val key = raw.substringBeforeLast('_')
                val id = raw.substringAfterLast('_').toLong()
                require(key != raw && id > 0)
                when (tab) {
                    PlannerListTab.UPCOMING, PlannerListTab.PAST ->
                        Visit(LocalDate.parse(key.substringBefore('T')), LocalTime.parse(key.substringAfter('T'), HH_MM), id)
                    PlannerListTab.CANCELED -> Canceled(Instant.ofEpochMilli(key.toLong()), id)
                }
            }.getOrElse { throw AppException(ErrorType.INVALID_REQUEST, "잘못된 커서 tab=$tab cursor=$raw") }

        /** 목록의 마지막 항목 다음부터 읽는 커서. */
        fun after(
            tab: PlannerListTab,
            last: PlannerSummary,
        ): PlannerListCursor =
            when (tab) {
                PlannerListTab.UPCOMING, PlannerListTab.PAST -> Visit(last.visitDate, last.startTime, last.id)
                PlannerListTab.CANCELED -> Canceled(requireNotNull(last.canceledAt).toInstant(), last.id)
            }
    }
}

/** 목록 항목. stops 전체 대신 개수와 첫 방문지만. */
data class PlannerSummary(
    val id: Long,
    val status: PlannerStatus,
    val title: String,
    val areaId: Int,
    val visitDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val totalMin: Int,
    val stopCount: Int,
    val firstStop: FirstStop?,
    val canceledAt: OffsetDateTime?,
    /** favorite_area.area. 서비스에서 붙인다. */
    val areaName: String? = null,
) {
    data class FirstStop(
        val title: String,
        val imageUrl: String?,
    )
}

/** 목록 한 페이지. nextCursor 는 hasNext 일 때만. */
data class PlannerSummaryPage(
    val content: List<PlannerSummary>,
    val hasNext: Boolean,
    val nextCursor: String?,
)
