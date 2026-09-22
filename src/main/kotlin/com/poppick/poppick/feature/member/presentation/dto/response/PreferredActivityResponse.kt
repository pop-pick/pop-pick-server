package com.poppick.poppick.feature.member.presentation.dto.response

import com.poppick.poppick.feature.member.domain.PreferredActivity

data class PreferredActivityResponse(
    val id: Int,
    val activity: String,
) {
    companion object {
        fun from(preferredActivity: PreferredActivity) = PreferredActivityResponse(preferredActivity.id, preferredActivity.activity)
    }
}
