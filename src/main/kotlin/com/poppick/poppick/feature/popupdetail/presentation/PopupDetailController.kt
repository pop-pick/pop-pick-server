package com.poppick.poppick.feature.popupdetail.presentation

import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import com.poppick.poppick.feature.popupdetail.presentation.dto.response.PopupDetailResponse
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.security.domain.AuthMember
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val X_REAL_IP = "X-Real-IP"
private const val X_FORWARDED_FOR = "X-Forwarded-For"

@Tag(name = "PopupDetail", description = "팝업 상세 APIs")
@RestController
@RequestMapping("/api/v1/popups")
class PopupDetailController(
    private val popupDetailService: PopupDetailService,
) {
    @Operation(
        summary = "팝업 상세 조회",
        description =
            "팝업 ID로 상세 정보를 조회합니다. 비회원도 조회할 수 있습니다.\n\n" +
                "조회 시 viewCount가 1 증가하며, 증가된 조회수가 응답에 포함됩니다. " +
                "같은 사용자가 10분 안에 다시 조회하면 증가하지 않습니다.\n\n" +
                "존재하지 않는 팝업이면 404(E404)를 반환합니다.",
    )
    @GetMapping("/{popupId}")
    fun findPopupDetail(
        @PathVariable popupId: Long,
        // 비회원이면 principal 이 "anonymousUser" 문자열이라 타입이 맞지 않아 null 이 들어온다.
        @Parameter(hidden = true) @AuthenticationPrincipal authMember: AuthMember?,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<PopupDetailResponse>> {
        val viewer = authMember?.let { PopupViewer.member(it.member.memberKey) } ?: request.anonymousViewer()
        val popup = popupDetailService.findPopupDetail(popupId, viewer)
        val categoryNames = popupDetailService.findCategoryNames()
        val areaNames = popupDetailService.findAreaNames()

        return ResponseEntity.ok(ApiResponse.success(PopupDetailResponse.from(popup, categoryNames, areaNames)))
    }

    private fun HttpServletRequest.anonymousViewer() =
        PopupViewer.anonymous(
            xRealIp = getHeader(X_REAL_IP),
            xForwardedFor = getHeader(X_FORWARDED_FOR),
            remoteAddr = remoteAddr,
            userAgent = getHeader(HttpHeaders.USER_AGENT),
        )
}
