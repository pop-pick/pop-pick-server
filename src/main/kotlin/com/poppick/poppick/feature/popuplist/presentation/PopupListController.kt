package com.poppick.poppick.feature.popuplist.presentation

import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListPageResponse
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListResponse
import com.poppick.poppick.global.paging.CursorDefault
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.response.ApiResponse
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
            "오픈했고 종료되지 않은 팝업을 sort 순서로 조회한다. keyword 로 이름 · 브랜드 · 주소를 검색한다.\n\n" +
                "정렬(sort): latest(기본) = 오픈일 최신순(오픈일 없는 팝업은 맨 뒤, 같은 오픈일은 최근 등록순), " +
                "popular = 인기순(상세 조회수 많은 순, 같은 조회수는 최근 등록순).\n\n" +
                "페이지 조회: 첫 페이지는 cursor 없이 요청한다. 응답의 hasNext 가 true 이면 nextCursor 가 함께 내려가며, " +
                "그 값을 그대로 다음 요청의 cursor 에 넣는다. hasNext 가 false 이면 nextCursor 는 null 이다.\n\n" +
                "keyword 나 sort 가 바뀌면 이전 cursor 를 재사용하지 말고 cursor 없이 첫 페이지부터 다시 요청한다. " +
                "다른 sort 의 cursor 를 보내면 400(E400).\n\n" +
                "인기순은 조회수가 계속 바뀌는 값이라, 페이지를 넘기는 사이 조회수가 오른 팝업은 순서가 바뀌어 " +
                "다음 페이지에서 빠질 수 있다.",
    )
    @Parameters(
        Parameter(
            name = "sort",
            `in` = ParameterIn.QUERY,
            description =
                "정렬. latest = 오픈일 최신순, popular = 인기순(상세 조회수). 생략하면 latest. " +
                    "대소문자는 구분하지 않으며, 그 밖의 값이면 400(E400).",
            schema = Schema(type = "string", allowableValues = ["latest", "popular"], defaultValue = "latest"),
        ),
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
        @Parameter(description = "검색어. 이름 · 브랜드 · 도로명 주소 · 지번 주소 부분 일치(대소문자 무시). 생략하면 전체 목록.")
        @RequestParam(required = false)
        keyword: String?,
        @Parameter(hidden = true) @RequestParam(required = false) sort: String?,
        @Parameter(hidden = true) @CursorDefault cursorable: Cursorable<String>,
    ): ResponseEntity<ApiResponse<PopupListPageResponse>> {
        val sortType = PopupSortType.from(sort)
        val cursor = cursorable.cursor?.takeIf { it.isNotBlank() }?.let { PopupListPageResponse.decodeCursor(it, sortType) }
        val slice = popupListService.findPopups(keyword, sortType, Cursorable(cursor, cursorable.limit))

        return ResponseEntity.ok(ApiResponse.success(PopupListPageResponse.from(slice, sortType)))
    }

    @Operation(
        summary = "지금 인기 있는 팝업",
        description =
            "홈 화면용 인기 팝업을 최대 3개 조회한다. 로그인하지 않아도 조회할 수 있다.\n\n" +
                "기준은 목록의 sort=popular 와 같다: 오픈했고 종료되지 않은 팝업 중 상세 조회수 많은 순, " +
                "같은 조회수는 최근 등록순. 노출 대상이 3개보다 적으면 있는 만큼만 내려간다.\n\n" +
                "이 API 호출로는 조회수가 오르지 않는다(조회수는 상세 조회에서만 오른다).",
    )
    @GetMapping("/popular")
    fun findPopularPopups(): ResponseEntity<ApiResponse<List<PopupListResponse>>> =
        ResponseEntity.ok(ApiResponse.success(popupListService.findPopularPopups().map { PopupListResponse.from(it) }))
}
