package com.poppick.poppick.feature.popupdetail.presentation

import com.poppick.poppick.feature.member.domain.Member
import com.poppick.poppick.feature.popupdetail.business.PopupDetailService
import com.poppick.poppick.feature.popupdetail.presentation.dto.response.PopupDetailResponse
import com.poppick.poppick.global.response.ApiResponse
import com.poppick.poppick.security.annotation.OptionalAuthMember
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "PopupDetail", description = "팝업 상세 APIs")
@RestController
@RequestMapping("/api/v1/popups")
class PopupDetailController(
    private val popupDetailService: PopupDetailService,
) {
    @Operation(
        summary = "팝업 상세 조회",
        description =
            "팝업 상세 정보를 조회한다.\n\n" +
                "Authorization 헤더는 선택. 보내면 wished 에 찜 여부가 담기고, 없으면 false.",
    )
    @GetMapping("/{popupId}")
    fun findPopupDetail(
        @OptionalAuthMember member: Member?,
        @PathVariable popupId: Long,
    ): ResponseEntity<ApiResponse<PopupDetailResponse>> {
        val detail = popupDetailService.findPopupDetail(member?.memberKey, popupId)
        return ResponseEntity.ok(ApiResponse.success(PopupDetailResponse.from(detail.popup, detail.wished)))
    }
}
