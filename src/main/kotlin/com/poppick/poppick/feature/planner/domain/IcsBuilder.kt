package com.poppick.poppick.feature.planner.domain

import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 일정 1건짜리 iCalendar(RFC 5545) 문서.
 * VTIMEZONE 블록은 넣지 않는다. iOS · Google · Outlook 모두 TZID=Asia/Seoul(IANA 이름)을 인식하고,
 * 한국은 서머타임이 없어 규칙을 적어 줄 이유도 없다.
 */
object IcsBuilder {
    private const val CRLF = "\r\n"
    private const val MAX_LINE_OCTETS = 75
    private const val TIME_ZONE = "Asia/Seoul"
    private val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun build(
        planner: Planner,
        areaName: String,
        shareUrl: String? = null,
        now: Instant = Instant.now(),
    ): String =
        listOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//POP PICK//Planner//KO",
            "CALSCALE:GREGORIAN",
            "METHOD:PUBLISH",
            "BEGIN:VEVENT",
            "UID:planner-${planner.id}@poppick",
            "DTSTAMP:${UTC.format(now)}",
            "DTSTART;TZID=$TIME_ZONE:${local(planner.visitDate, planner.startTime)}",
            "DTEND;TZID=$TIME_ZONE:${local(planner.visitDate, planner.endTime)}",
            "SUMMARY:${escape(planner.title)}",
            "LOCATION:${escape(CourseDescription.location(planner, areaName))}",
            "DESCRIPTION:${escape(CourseDescription.build(planner, shareUrl))}",
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString(separator = CRLF, postfix = CRLF) { fold(it) }

    private fun local(
        date: LocalDate,
        time: LocalTime,
    ) = date.atTime(time).format(LOCAL)

    /** TEXT 값 이스케이프: \ ; , 와 줄바꿈(\n). */
    internal fun escape(value: String) =
        value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")

    /**
     * 75옥텟을 넘는 줄을 접는다. 이어지는 줄은 공백 한 칸으로 시작하고 그 공백도 75옥텟에 포함된다.
     * 코드 포인트 단위로 잘라 UTF-8 문자 중간에서 끊지 않는다.
     */
    internal fun fold(line: String): String {
        if (line.toByteArray(UTF_8).size <= MAX_LINE_OCTETS) return line

        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var octets = 0
        line.codePoints().forEach { codePoint ->
            val size = String(Character.toChars(codePoint)).toByteArray(UTF_8).size
            if (octets + size > MAX_LINE_OCTETS) {
                lines += current.toString()
                current.clear()
                current.append(' ')
                octets = 1
            }
            current.appendCodePoint(codePoint)
            octets += size
        }
        lines += current.toString()
        return lines.joinToString(CRLF)
    }
}
