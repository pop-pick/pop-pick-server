package com.poppick.poppick.feature.popuplist.presentation

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListPageResponse
import com.poppick.poppick.global.paging.CursorDefault
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.security.annotation.OptionalAuthMember
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Popup", description = "팝업 APIs")
@RestController
@RequestMapping("/api/v1/popups")
class PopupListController(
    private val popupListService: PopupListService,
) {
    @Operation(
        summary = "팝업 목록",
        description =
            "오픈했고 종료되지 않은 팝업을 오픈일 최신순으로 조회한다(오픈일 없는 팝업은 맨 뒤). " +
                "keyword 로 이름 · 브랜드 · 주소를 검색한다.\n\n" +
                "페이지 조회: 첫 페이지는 cursor 없이 요청한다. 응답의 hasNext 가 true 이면 nextCursor 가 함께 내려가며, " +
                "그 값을 그대로 다음 요청의 cursor 에 넣는다. hasNext 가 false 이면 nextCursor 는 null 이다.\n\n" +
                "keyword 가 바뀌면 이전 cursor 를 재사용하지 말고 cursor 없이 첫 페이지부터 다시 요청한다.\n\n" +
                "Authorization 헤더는 선택. 보내면 각 항목의 wished 에 찜 여부가 담기고, 없으면 전부 false.",
    )
    @Parameters(
        Parameter(
            name = "cursor",
            `in` = ParameterIn.QUERY,
            description =
                "이전 응답의 nextCursor 값을 그대로 넣는다. 첫 페이지는 생략한다. " +
                    "서버 내부 형식이므로 해석하거나 직접 만들지 않는다. 형식이 올바르지 않으면 400(E400).",
            schema = Schema(type = "string"),
            example = "MjAyNi0wOS0yMDoxNzE1",
        ),
        Parameter(
            name = "limit",
            `in` = ParameterIn.QUERY,
            description = "페이지 크기(1~50). 생략하면 10.",
            schema = Schema(type = "integer", defaultValue = "10", minimum = "1", maximum = "50"),
        ),
    )
    @GetMapping
    fun findPopups(
        @OptionalAuthMember member: Member?,
        @Parameter(description = "검색어. 이름 · 브랜드 · 도로명 주소 · 지번 주소 부분 일치(대소문자 무시). 생략하면 전체 목록.")
        @RequestParam(required = false)
        keyword: String?,
        @Parameter(hidden = true) @CursorDefault cursorable: Cursorable<String>,
    ): ResponseEntity<ApiResponse<PopupListPageResponse>> {
        val cursor = cursorable.cursor?.takeIf { it.isNotBlank() }?.let { PopupListPageResponse.decodeCursor(it) }
        val slice = popupListService.findPopups(member?.memberKey, keyword, Cursorable(cursor, cursorable.limit))

        return ResponseEntity.ok(ApiResponse.success(PopupListPageResponse.from(slice)))
    }
}
