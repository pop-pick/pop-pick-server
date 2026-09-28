package com.poppick.poppick.feature.planner.presentation.dto.response

import com.poppick.poppick.feature.planner.domain.PlannerShare

data class PlannerShareResponse(
    val shareToken: String,
    /** 앱은 이 URL 을 그대로 공유한다. */
    val shareUrl: String,
) {
    companion object {
        fun from(share: PlannerShare) = PlannerShareResponse(share.shareToken, share.shareUrl)
    }
}
