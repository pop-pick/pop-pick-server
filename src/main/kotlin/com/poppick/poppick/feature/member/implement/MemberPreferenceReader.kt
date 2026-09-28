package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.InterestCategoryRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberInterestCategoryRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberPreferredActivityRepository
import com.poppick.poppick.feature.member.dataaccess.repository.PreferredActivityRepository
import com.poppick.poppick.feature.member.domain.MemberPreference
import org.springframework.stereotype.Component

/** member_interest_category ⋈ interest_category, member_preferred_activity ⋈ preferred_activity. 이름은 id 오름차순. */
@Component
class MemberPreferenceReader(
    private val memberInterestCategoryRepository: MemberInterestCategoryRepository,
    private val interestCategoryRepository: InterestCategoryRepository,
    private val memberPreferredActivityRepository: MemberPreferredActivityRepository,
    private val preferredActivityRepository: PreferredActivityRepository,
) {
    fun find(memberKey: String): MemberPreference {
        val categoryIds = memberInterestCategoryRepository.findAllByMemberKey(memberKey).map { it.interestCategoryId }.toSet()
        val activityIds = memberPreferredActivityRepository.findAllByMemberKey(memberKey).map { it.preferredActivityId }.toSet()

        return MemberPreference(
            interestCategories =
                if (categoryIds.isEmpty()) {
                    emptyList()
                } else {
                    interestCategoryRepository.findAllById(categoryIds).sortedBy { it.id }.map { it.category }
                },
            preferredActivities =
                if (activityIds.isEmpty()) {
                    emptyList()
                } else {
                    preferredActivityRepository.findAllById(activityIds).sortedBy { it.id }.map { it.activity }
                },
        )
    }
}
