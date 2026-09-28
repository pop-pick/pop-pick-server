package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.MemberFavoriteAreaRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberInterestCategoryRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberPreferredActivityRepository
import com.poppick.poppick.feature.member.domain.MemberPreference
import org.springframework.stereotype.Component

/** 회원 온보딩 선택값(member_favorite_area · member_interest_category · member_preferred_activity). */
@Component
class MemberPreferenceReader(
    private val memberFavoriteAreaRepository: MemberFavoriteAreaRepository,
    private val memberInterestCategoryRepository: MemberInterestCategoryRepository,
    private val memberPreferredActivityRepository: MemberPreferredActivityRepository,
) {
    fun find(memberKey: String) =
        MemberPreference(
            favoriteAreaIds =
                memberFavoriteAreaRepository
                    .findAllByMemberKey(memberKey)
                    .map { it.favoriteAreaId }
                    .distinct()
                    .sorted(),
            interestCategoryIds =
                memberInterestCategoryRepository
                    .findAllByMemberKey(memberKey)
                    .map { it.interestCategoryId }
                    .distinct()
                    .sorted(),
            preferredActivityIds =
                memberPreferredActivityRepository
                    .findAllByMemberKey(memberKey)
                    .map { it.preferredActivityId }
                    .distinct()
                    .sorted(),
        )
}
