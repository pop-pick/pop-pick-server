package com.poppick.poppick.feature.popuplist.presentation

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popuplist.business.PopupListService
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListPageResponse
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupListResponse
import com.poppick.poppick.feature.popuplist.presentation.dto.response.PopupMapResponse
import com.poppick.poppick.global.paging.CursorDefault
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.security.annotation.AuthMember
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

        val categoryNames = popupListService.findCategoryNames()

        return ResponseEntity.ok(ApiResponse.success(PopupListPageResponse.from(slice, sortType, categoryNames)))
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
    fun findPopularPopups(): ResponseEntity<ApiResponse<List<PopupListResponse>>> {
        val popups = popupListService.findPopularPopups()
        val categoryNames = popupListService.findCategoryNames()

        return ResponseEntity.ok(ApiResponse.success(popups.map { PopupListResponse.from(it, categoryNames) }))
    }

    @Operation(
        summary = "회원 추천 팝업",
        description =
            "홈 화면용 회원 추천 팝업을 최대 3개 조회한다. 로그인한 회원만 조회할 수 있다(비회원은 401 · E1000).\n\n" +
                "온보딩에서 고른 관심 카테고리 또는 선호 지역 중 하나라도 맞는 팝업을 고른다. " +
                "노출 조건(오픈했고 종료되지 않음)과 순서(상세 조회수 많은 순, 같은 조회수는 최근 등록순)는 인기 팝업 API 와 같다.\n\n" +
                "맞는 팝업이 3개보다 적으면 인기 팝업 순서대로 이미 담긴 팝업을 빼고 채운다. " +
                "관심 카테고리 · 선호 지역을 하나도 고르지 않은 회원은 인기 팝업과 같은 결과다. " +
                "인기 팝업 API 결과와 겹칠 수 있다. 노출 대상이 3개보다 적으면 있는 만큼만 내려간다.\n\n" +
                "이 API 호출로는 조회수가 오르지 않는다.",
    )
    @GetMapping("/recommended")
    fun findRecommendedPopups(
        @AuthMember member: Member,
    ): ResponseEntity<ApiResponse<List<PopupListResponse>>> {
        val popups = popupListService.findRecommendedPopups(member.memberKey)
        val categoryNames = popupListService.findCategoryNames()

        return ResponseEntity.ok(ApiResponse.success(popups.map { PopupListResponse.from(it, categoryNames) }))
    }

    @Operation(
        summary = "지도 팝업",
        description =
            "지도 화면 영역(남서 · 북동 좌표) 안의 노출 중인 팝업을 한 번에 조회한다. 로그인하지 않아도 조회할 수 있다.\n\n" +
                "노출 조건(오픈했고 종료되지 않음)과 keyword 검색 조건은 목록 API 와 같고, 좌표가 영역 안(경계 포함)인 팝업만 담는다. " +
                "cursor · 정렬 · 페이지 없이 영역 안 마커를 모두 내려준다(서버 안전 상한 500건, 넘으면 조회수 많은 순으로 자른다).\n\n" +
                "응답만으로 마커와 하단 카드를 그린다. 카드 표시용으로 상세 API 를 미리 호출하지 않는다(상세 API 는 조회수를 올린다). " +
                "이 API 호출로는 조회수가 오르지 않는다.\n\n" +
                "네 좌표는 모두 필수이며, 누락 · 숫자 아님 · 위도(-90~90) · 경도(-180~180) 범위 밖 · swLat > neLat · swLng > neLng 이면 400(E400).",
    )
    @Parameters(
        Parameter(name = "swLat", `in` = ParameterIn.QUERY, required = true, description = "남서쪽 위도", example = "37.50"),
        Parameter(name = "swLng", `in` = ParameterIn.QUERY, required = true, description = "남서쪽 경도", example = "126.95"),
        Parameter(name = "neLat", `in` = ParameterIn.QUERY, required = true, description = "북동쪽 위도", example = "37.60"),
        Parameter(name = "neLng", `in` = ParameterIn.QUERY, required = true, description = "북동쪽 경도", example = "127.10"),
    )
    @GetMapping("/map")
    fun findMapPopups(
        @Parameter(description = "검색어. 목록 API 와 같다(이름 · 브랜드 · 도로명 주소 · 지번 주소 부분 일치). 생략하면 영역 안 전체.")
        @RequestParam(required = false)
        keyword: String?,
        // 누락을 400 으로 응답하려고 선택 파라미터로 받아 MapBounds 에서 필수 여부를 검증한다.
        @Parameter(hidden = true) @RequestParam(required = false) swLat: Double?,
        @Parameter(hidden = true) @RequestParam(required = false) swLng: Double?,
        @Parameter(hidden = true) @RequestParam(required = false) neLat: Double?,
        @Parameter(hidden = true) @RequestParam(required = false) neLng: Double?,
    ): ResponseEntity<ApiResponse<List<PopupMapResponse>>> {
        val bounds = MapBounds.of(swLat, swLng, neLat, neLng)
        val popups = popupListService.findMapPopups(keyword, bounds)
        val categoryNames = popupListService.findCategoryNames()
        val areaNames = popupListService.findAreaNames()

        return ResponseEntity.ok(ApiResponse.success(popups.map { PopupMapResponse.from(it, categoryNames, areaNames) }))
    }
}
