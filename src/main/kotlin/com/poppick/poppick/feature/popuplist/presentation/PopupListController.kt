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
            "오픈했고 종료되지 않은 팝업 목록을 조회합니다. 비회원도 조회할 수 있습니다.\n\n" +
                "latest는 오픈일 최신순(오픈일이 없는 팝업은 마지막), popular는 상세 조회수 많은 순입니다. " +
                "기준 값이 같으면 최근 등록순입니다.\n\n" +
                "areaId와 keyword를 함께 보내면 두 조건을 모두 만족하는 팝업만 반환합니다. " +
                "지역이 분류되지 않은 팝업은 areaId 필터 결과에 포함되지 않습니다.\n\n" +
                "첫 페이지는 cursor 없이 요청하고, 다음 페이지는 응답의 nextCursor를 cursor로 보냅니다. " +
                "keyword · sort · areaId가 바뀌면 cursor 없이 첫 페이지부터 다시 요청합니다.\n\n" +
                "인기순은 페이지를 넘기는 사이 조회수가 바뀌면 일부 팝업의 순서가 바뀌거나 누락될 수 있습니다.\n\n" +
                "Authorization 헤더는 선택입니다. 보내면 각 항목의 wished에 찜 여부가 담기고, 없으면 전부 false입니다.",
    )
    @Parameters(
        Parameter(
            name = "areaId",
            `in` = ParameterIn.QUERY,
            description =
                "지역 ID. GET /api/v1/onboardings/favorite-areas의 id를 사용합니다. " +
                    "생략하면 전체 지역을 조회하고, 없는 ID면 빈 목록을 반환합니다.",
            schema = Schema(type = "integer"),
            example = "3",
        ),
        Parameter(
            name = "sort",
            `in` = ParameterIn.QUERY,
            description = "정렬 기준. 생략하면 latest이며 대소문자를 구분하지 않습니다. 그 외 값은 400(E400)을 반환합니다.",
            schema = Schema(type = "string", allowableValues = ["latest", "popular"], defaultValue = "latest"),
        ),
        Parameter(
            name = "cursor",
            `in` = ParameterIn.QUERY,
            description =
                "이전 응답의 nextCursor 값입니다. 첫 페이지는 생략합니다. " +
                    "형식이 잘못되었거나 다른 sort의 cursor면 400(E400)을 반환합니다.",
            schema = Schema(type = "string"),
            example = "MjAyNi0wOS0yMDoxNzE1",
        ),
        Parameter(
            name = "limit",
            `in` = ParameterIn.QUERY,
            description = "페이지 크기(1~50). 생략하면 10이며, 범위를 벗어나면 400(E400)을 반환합니다.",
            schema = Schema(type = "integer", defaultValue = "10", minimum = "1", maximum = "50"),
        ),
    )
    @GetMapping
    fun findPopups(
        @OptionalAuthMember member: Member?,
        @Parameter(
            description =
                "검색어. 팝업명 · 브랜드 · 주소 · 지역 이름에 포함된 팝업을 찾습니다(대소문자 무시). " +
                    "지역 이름과 일치하면 주소에 해당 단어가 없어도 그 지역 팝업이 포함됩니다. 예: 홍대",
        )
        @RequestParam(required = false)
        keyword: String?,
        @Parameter(hidden = true) @RequestParam(required = false) areaId: Int?,
        @Parameter(hidden = true) @RequestParam(required = false) sort: String?,
        @Parameter(hidden = true) @CursorDefault cursorable: Cursorable<String>,
    ): ResponseEntity<ApiResponse<PopupListPageResponse>> {
        val sortType = PopupSortType.from(sort)
        val cursor = cursorable.cursor?.takeIf { it.isNotBlank() }?.let { PopupListPageResponse.decodeCursor(it, sortType) }
        val slice = popupListService.findPopups(member?.memberKey, keyword, areaId, sortType, Cursorable(cursor, cursorable.limit))

        val categoryNames = popupListService.findCategoryNames()
        val areaNames = popupListService.findAreaNames()

        return ResponseEntity.ok(ApiResponse.success(PopupListPageResponse.from(slice, sortType, categoryNames, areaNames)))
    }

    @Operation(
        summary = "지금 인기 있는 팝업",
        description =
            "홈 화면의 인기 팝업을 최대 3개 조회합니다. 비회원도 조회할 수 있습니다.\n\n" +
                "정렬 기준은 목록 API의 sort=popular와 같습니다. 이 API는 조회수를 증가시키지 않습니다.\n\n" +
                "Authorization 헤더는 선택입니다. 보내면 각 항목의 wished에 찜 여부가 담기고, 없으면 전부 false입니다.",
    )
    @GetMapping("/popular")
    fun findPopularPopups(
        @OptionalAuthMember member: Member?,
    ): ResponseEntity<ApiResponse<List<PopupListResponse>>> {
        val popups = popupListService.findPopularPopups(member?.memberKey)
        val categoryNames = popupListService.findCategoryNames()
        val areaNames = popupListService.findAreaNames()

        return ResponseEntity.ok(
            ApiResponse.success(popups.map { PopupListResponse.from(it.popup, it.wished, categoryNames, areaNames) }),
        )
    }

    @Operation(
        summary = "회원 추천 팝업",
        description =
            "홈 화면의 회원 추천 팝업을 최대 3개 조회합니다. 로그인한 회원만 조회할 수 있으며, 비회원은 401(E1000)을 반환합니다.\n\n" +
                "회원의 관심 카테고리 또는 선호 지역과 일치하는 팝업을 인기 팝업 API와 같은 기준으로 정렬해 추천합니다. " +
                "3개보다 적으면 이미 포함된 팝업을 제외하고 인기 팝업으로 채웁니다.\n\n" +
                "관심 카테고리와 선호 지역이 모두 없으면 인기 팝업과 같은 결과를 반환합니다. " +
                "인기 팝업 API 결과와 겹칠 수 있으며, 이 API는 조회수를 증가시키지 않습니다.\n\n" +
                "각 항목의 wished에 로그인 회원의 찜 여부가 담깁니다.",
    )
    @GetMapping("/recommended")
    fun findRecommendedPopups(
        @AuthMember member: Member,
    ): ResponseEntity<ApiResponse<List<PopupListResponse>>> {
        val popups = popupListService.findRecommendedPopups(member.memberKey)
        val categoryNames = popupListService.findCategoryNames()
        val areaNames = popupListService.findAreaNames()

        return ResponseEntity.ok(
            ApiResponse.success(popups.map { PopupListResponse.from(it.popup, it.wished, categoryNames, areaNames) }),
        )
    }

    @Operation(
        summary = "지도 팝업",
        description =
            "지도 영역 안의 팝업을 페이지 없이 한 번에 조회합니다. 비회원도 조회할 수 있습니다.\n\n" +
                "노출 조건과 keyword 검색 기준은 목록 API와 같고, 영역 경계에 있는 팝업도 포함합니다. " +
                "최대 500건이며, 넘으면 조회수가 많은 순으로 반환합니다.\n\n" +
                "마커와 하단 카드는 이 응답만으로 표시합니다. 상세 API는 조회수를 증가시키므로 카드 표시용으로 호출하지 않습니다. " +
                "이 API는 조회수를 증가시키지 않습니다.\n\n" +
                "좌표 4개는 모두 필수입니다. 누락되었거나 숫자가 아니거나, 위도 -90~90 · 경도 -180~180 범위를 벗어나거나, " +
                "swLat > neLat 또는 swLng > neLng이면 400(E400)을 반환합니다.",
    )
    @Parameters(
        Parameter(name = "swLat", `in` = ParameterIn.QUERY, required = true, description = "남서쪽 위도", example = "37.50"),
        Parameter(name = "swLng", `in` = ParameterIn.QUERY, required = true, description = "남서쪽 경도", example = "126.95"),
        Parameter(name = "neLat", `in` = ParameterIn.QUERY, required = true, description = "북동쪽 위도", example = "37.60"),
        Parameter(name = "neLng", `in` = ParameterIn.QUERY, required = true, description = "북동쪽 경도", example = "127.10"),
    )
    @GetMapping("/map")
    fun findMapPopups(
        @Parameter(description = "검색어. 목록 API의 keyword와 같습니다. 생략하면 영역 안 전체를 조회합니다.")
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
