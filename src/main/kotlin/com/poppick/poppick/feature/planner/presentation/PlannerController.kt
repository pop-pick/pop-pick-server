package com.poppick.poppick.feature.planner.presentation

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.planner.business.PlannerService
import com.poppick.poppick.feature.planner.domain.PlannerListTab
import com.poppick.poppick.feature.planner.presentation.dto.request.PlannerGenerateRequest
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerCalendarResponse
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerCountsResponse
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerFormResponse
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerResponse
import com.poppick.poppick.feature.planner.presentation.dto.response.PlannerSummaryResponse
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.global.response.PageResponse
import com.poppick.poppick.security.annotation.AuthMember
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as DocResponse

// 에러 응답은 공통 ApiResponse 래퍼(resultType = ERROR, error.errorCode · message · data).
private const val UNAUTHORIZED = "E1000 REQUIRED_AUTH(토큰 없음) · E1004 EXPIRED_JWT 등 인증 실패"
private const val FORBIDDEN = "E3001 PLANNER_FORBIDDEN: 다른 회원의 플래너"
private const val NOT_FOUND = "E3000 PLANNER_NOT_FOUND"

@Tag(name = "Planner", description = "AI 플래너 · 내 일정")
@RestController
@RequestMapping("/api/v1/planners")
class PlannerController(
    private val plannerService: PlannerService,
) {
    @Operation(
        summary = "플래너 입력 폼",
        description = "생성 화면의 기본값(온보딩 값) · 선택지(지역 · 카테고리 · 활동 · 동행 · 소요시간) · 방문일/시작 시각 범위를 준다.",
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "성공"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping("/form")
    fun form(
        @AuthMember member: Member,
    ): ResponseEntity<ApiResponse<PlannerFormResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerFormResponse.from(plannerService.form(member.memberKey))))

    @Operation(
        summary = "플래너 생성(DRAFT)",
        description =
            "후보 검색 → LLM 코스 선정 → 도보 경로로 DRAFT 를 만든다. 동기 4~9초, 앱은 로딩 화면 필요.\n\n" +
                "회원의 기존 DRAFT 는 새 DRAFT 로 교체된다(회원당 최대 1개). 실패하면 아무것도 저장하지 않고 기존 DRAFT 도 남는다.\n\n" +
                "에러: E400 INVALID_REQUEST(필수 값 누락 · 없는 id · note 200자 초과), E3002 INVALID_VISIT_DATE(오늘~30일), " +
                "E3003 INVALID_START_TIME(08:00~20:00, 오늘이면 30분 뒤부터), E3004 INSUFFICIENT_POPUPS, " +
                "E3006 GENERATION_FAILED, E3007 ROUTE_FAILED",
    )
    @ApiResponses(
        DocResponse(responseCode = "201", description = "DRAFT 생성"),
        DocResponse(
            responseCode = "400",
            description = "E400 INVALID_REQUEST · E3002 INVALID_VISIT_DATE · E3003 INVALID_START_TIME",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "422",
            description = "E3004 INSUFFICIENT_POPUPS: 조건에 맞는 팝업이 코스 최소 개수보다 적다",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "502",
            description = "E3006 GENERATION_FAILED(LLM) · E3007 ROUTE_FAILED(길찾기)",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @PostMapping("/generate")
    fun generate(
        @AuthMember member: Member,
        @Valid @RequestBody request: PlannerGenerateRequest,
    ): ResponseEntity<ApiResponse<PlannerResponse>> =
        ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(PlannerResponse.from(plannerService.generate(member.memberKey, request.toCommand()))))

    @Operation(
        summary = "플래너 확정(DRAFT → SCHEDULED)",
        description =
            "DRAFT 만 가능. 확정 시각이 등록일(confirmedAt)이 된다.\n\n" +
                "에러: E3005 INVALID_PLANNER_STATUS(DRAFT 아님), E3002 INVALID_VISIT_DATE(방문일 경과), E3001 · E3000",
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "확정"),
        DocResponse(
            responseCode = "400",
            description = "E3002 INVALID_VISIT_DATE: 방문일이 지난 DRAFT",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "403",
            description = FORBIDDEN,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "404",
            description = NOT_FOUND,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "409",
            description = "E3005 INVALID_PLANNER_STATUS: DRAFT 가 아니다",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @PostMapping("/{plannerId}/confirm")
    fun confirm(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<ApiResponse<PlannerResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerResponse.from(plannerService.confirm(member.memberKey, plannerId))))

    @Operation(
        summary = "플래너 상세",
        description = "소유자만. 상태 무관(DRAFT · SCHEDULED · CANCELED).\n\n에러: E3001 PLANNER_FORBIDDEN, E3000 PLANNER_NOT_FOUND",
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "성공"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "403",
            description = FORBIDDEN,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "404",
            description = NOT_FOUND,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping("/{plannerId}")
    fun get(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<ApiResponse<PlannerResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerResponse.from(plannerService.get(member.memberKey, plannerId))))

    /** "내 일정" 목록. size 는 1~50(Cursorable 검증). cursor 는 이전 응답의 nextCursor. */
    @Operation(
        summary = "내 일정 목록",
        description =
            "탭별 커서 페이지. DRAFT 는 어느 탭에도 없다.\n\n" +
                "- UPCOMING: SCHEDULED, 방문일 >= 오늘(KST). 방문일 · 시작 시각 오름차순\n" +
                "- PAST: SCHEDULED, 방문일 < 오늘. 방문일 · 시작 시각 내림차순\n" +
                "- CANCELED: CANCELED. 취소 시각 내림차순\n\n" +
                "다음 페이지는 응답의 nextCursor 를 cursor 로 넘긴다(hasNext = false 면 NULL).\n\n" +
                "에러: E400 INVALID_REQUEST(잘못된 tab · tab 과 맞지 않는 cursor), E400 INVALID_PAGING_SIZE(size 1~50 밖)",
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "성공"),
        DocResponse(
            responseCode = "400",
            description = "E400 INVALID_REQUEST · INVALID_PAGING_SIZE",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping
    fun list(
        @AuthMember member: Member,
        @Parameter(description = "탭") @RequestParam(defaultValue = "UPCOMING") tab: PlannerListTab,
        @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략", example = "2026-10-03T14:00_12")
        @RequestParam(required = false)
        cursor: String?,
        @Parameter(description = "페이지 크기 1~50") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PageResponse<PlannerSummaryResponse>>> {
        val page = plannerService.list(member.memberKey, tab, Cursorable(cursor, size))
        return ResponseEntity.ok(
            ApiResponse.success(PageResponse(page.content.map { PlannerSummaryResponse.from(it) }, page.hasNext, page.nextCursor)),
        )
    }

    @Operation(
        summary = "내 일정 탭 건수",
        description = "탭 라벨용 건수. 조건은 목록 탭과 같고 DRAFT 는 세지 않는다.",
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "성공"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping("/counts")
    fun counts(
        @AuthMember member: Member,
    ): ResponseEntity<ApiResponse<PlannerCountsResponse>> =
        ResponseEntity.ok(ApiResponse.success(PlannerCountsResponse.from(plannerService.counts(member.memberKey))))

    /** SCHEDULED 는 취소, DRAFT 는 삭제. */
    @Operation(
        summary = "일정 취소 · DRAFT 삭제",
        description =
            "SCHEDULED → CANCELED(지난 일정 포함, 복구 없음), DRAFT → 물리 삭제.\n\n" +
                "에러: E3005 INVALID_PLANNER_STATUS(이미 CANCELED), E3001 PLANNER_FORBIDDEN, E3000 PLANNER_NOT_FOUND",
    )
    @ApiResponses(
        DocResponse(responseCode = "204", description = "취소 또는 삭제"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "403",
            description = FORBIDDEN,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "404",
            description = NOT_FOUND,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "409",
            description = "E3005 INVALID_PLANNER_STATUS: 이미 취소됨",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @DeleteMapping("/{plannerId}")
    fun cancel(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<Unit> {
        plannerService.cancel(member.memberKey, plannerId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "캘린더 추가 링크",
        description = "구글 캘린더 일정 추가 링크와 .ics 다운로드 경로. 소유자만, 상태 무관.\n\n에러: E3001 PLANNER_FORBIDDEN, E3000 PLANNER_NOT_FOUND",
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "성공"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "403",
            description = FORBIDDEN,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "404",
            description = NOT_FOUND,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping("/{plannerId}/calendar")
    fun calendar(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<ApiResponse<PlannerCalendarResponse>> =
        ResponseEntity.ok(
            ApiResponse.success(
                PlannerCalendarResponse(
                    googleCalendarUrl = plannerService.calendar(member.memberKey, plannerId),
                    icsUrl = "/api/v1/planners/$plannerId/calendar.ics",
                ),
            ),
        )

    @Operation(
        summary = ".ics 다운로드",
        description =
            "iCalendar 파일(첨부, poppick-planner-{plannerId}.ics). 코스 전체가 VEVENT 1개(설명에 방문지 목록). 인증 헤더가 필요하다.\n\n" +
                "에러(JSON): E3001 PLANNER_FORBIDDEN, E3000 PLANNER_NOT_FOUND",
    )
    @ApiResponses(
        DocResponse(
            responseCode = "200",
            description = "text/calendar 본문",
            content = [Content(mediaType = "text/calendar", schema = Schema(type = "string", format = "binary"))],
        ),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "403",
            description = FORBIDDEN,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "404",
            description = NOT_FOUND,
            content = [Content(mediaType = "application/json", schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping("/{plannerId}/calendar.ics", produces = ["text/calendar"])
    fun calendarIcs(
        @AuthMember member: Member,
        @PathVariable plannerId: Long,
    ): ResponseEntity<ByteArray> =
        CalendarFileResponse.of(plannerService.calendarIcs(member.memberKey, plannerId), "poppick-planner-$plannerId.ics")
}
