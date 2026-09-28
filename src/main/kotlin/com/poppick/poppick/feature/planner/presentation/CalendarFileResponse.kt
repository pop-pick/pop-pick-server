package com.poppick.poppick.feature.planner.presentation

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.nio.charset.StandardCharsets.UTF_8

/** .ics 다운로드 응답. */
internal object CalendarFileResponse {
    private val TEXT_CALENDAR = MediaType("text", "calendar", UTF_8)

    fun of(
        ics: String,
        filename: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(TEXT_CALENDAR)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(filename)
                    .build()
                    .toString(),
            ).body(ics.toByteArray(UTF_8))
}
