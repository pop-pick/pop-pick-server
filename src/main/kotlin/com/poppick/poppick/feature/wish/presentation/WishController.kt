package com.poppick.poppick.feature.wish.presentation

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.wish.business.WishService
import com.poppick.poppick.feature.wish.presentation.dto.response.WishResponse
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import com.poppick.poppick.global.paging.CursorDefault
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.global.response.PageResponse
import com.poppick.poppick.security.annotation.AuthMember
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as DocResponse

// 에러 응답은 공통 ApiResponse 래퍼(resultType = ERROR, error.errorCode · message · data).
private const val UNAUTHORIZED = "E1000 REQUIRED_AUTH(토큰 없음) · E1004 EXPIRED_JWT 등 인증 실패"

@Tag(name = "Wish", description = "팝업 찜")
@RestController
@RequestMapping("/api/v1")
class WishController(
    private val wishService: WishService,
) {
    @Operation(
        summary = "팝업 찜 등록",
        description =
            "멱등. 이미 찜한 팝업이어도 204. 종료된 팝업도 찜할 수 있다.\n\n" +
                "에러: E404 NOT_FOUND_DATA(없는 팝업)",
    )
    @ApiResponses(
        DocResponse(responseCode = "204", description = "찜 상태"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "404",
            description = "E404 NOT_FOUND_DATA: 없는 팝업",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @PutMapping("/popups/{popupId}/wish")
    fun wish(
        @AuthMember member: Member,
        @PathVariable popupId: Long,
    ): ResponseEntity<Unit> {
        wishService.wish(member.memberKey, popupId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "팝업 찜 해제",
        description = "멱등. 찜하지 않은 팝업 · 없는 팝업이어도 204.",
    )
    @ApiResponses(
        DocResponse(responseCode = "204", description = "찜 해제 상태"),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @DeleteMapping("/popups/{popupId}/wish")
    fun unwish(
        @AuthMember member: Member,
        @PathVariable popupId: Long,
    ): ResponseEntity<Unit> {
        wishService.unwish(member.memberKey, popupId)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "내 찜 목록",
        description =
            "최근 찜한 순. 종료된 팝업도 포함하며 ended 로 구분한다(팝업 목록 API 와 달리 숨기지 않는다).\n\n" +
                "다음 페이지는 응답의 nextCursor 를 cursor 로 넘긴다(hasNext = false 면 NULL).\n\n" +
                "에러: E400 INVALID_PAGING_PARAMETER(숫자가 아닌 cursor), E400 INVALID_PAGING_SIZE(limit 1~50 밖)",
    )
    @Parameters(
        Parameter(
            name = "cursor",
            `in` = ParameterIn.QUERY,
            description = "이전 응답의 nextCursor. 첫 페이지는 생략한다.",
            schema = Schema(type = "string"),
            example = "42",
        ),
        Parameter(
            name = "limit",
            `in` = ParameterIn.QUERY,
            description = "페이지 크기(1~50). 생략하면 10.",
            schema = Schema(type = "integer", defaultValue = "10", minimum = "1", maximum = "50"),
        ),
    )
    @ApiResponses(
        DocResponse(responseCode = "200", description = "성공"),
        DocResponse(
            responseCode = "400",
            description = "E400 INVALID_PAGING_PARAMETER · INVALID_PAGING_SIZE",
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
        DocResponse(
            responseCode = "401",
            description = UNAUTHORIZED,
            content = [Content(schema = Schema(implementation = ApiResponse::class))],
        ),
    )
    @GetMapping("/wishes")
    fun findWishes(
        @AuthMember member: Member,
        @Parameter(hidden = true) @CursorDefault cursorable: Cursorable<String>,
    ): ResponseEntity<ApiResponse<PageResponse<WishResponse>>> {
        val cursor =
            cursorable.cursor?.takeIf { it.isNotBlank() }?.let {
                it.toLongOrNull()?.takeIf { id -> id > 0 } ?: throw AppException(ErrorType.INVALID_PAGING_PARAMETER)
            }
        val slice = wishService.findWishes(member.memberKey, Cursorable(cursor, cursorable.limit))
        val nextCursor =
            slice.content
                .lastOrNull()
                ?.takeIf { slice.hasNext }
                ?.wish
                ?.id
                ?.toString()
        return ResponseEntity.ok(ApiResponse.success(PageResponse(slice.content.map { WishResponse.from(it) }, slice.hasNext, nextCursor)))
    }
}
