package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.entity.MemberDetailEntity
import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.entity.MemberFavoriteAreaEntity
import com.poppick.poppick.feature.member.dataaccess.entity.MemberInterestCategoryEntity
import com.poppick.poppick.feature.member.dataaccess.entity.MemberPreferredActivityEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberDetailRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberFavoriteAreaRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberInterestCategoryRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberPreferredActivityRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.member.domain.NewMember
import com.poppick.poppick.feature.member.domain.OnboardingInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberRegistrar(
    private val memberRepository: MemberRepository,
    private val memberDetailRepository: MemberDetailRepository,
    private val memberFavoriteAreaRepository: MemberFavoriteAreaRepository,
    private val memberInterestCategoryRepository: MemberInterestCategoryRepository,
    private val memberPreferredActivityRepository: MemberPreferredActivityRepository,
) {
    @Transactional
    fun register(newMember: NewMember) = memberRepository.save(MemberEntity.from(newMember)).memberKey

    @Transactional
    fun registerDetails(onboardingInfo: OnboardingInfo) {
        memberDetailRepository.save(MemberDetailEntity.from(onboardingInfo))

        // TODO: 각 ID가 있는지 검증 할까말까...
        registerFavoriteAreas(onboardingInfo)
        registerInterestCategories(onboardingInfo)
        registerPreferredActivities(onboardingInfo)
    }

    @Transactional
    fun registerPreferredActivities(onboardingInfo: OnboardingInfo) {
        memberPreferredActivityRepository.saveAll(
            onboardingInfo.preferredActivityIds.map {
                MemberPreferredActivityEntity.new(
                    preferredActivityId = it,
                    memberKey = onboardingInfo.memberKey,
                )
            },
        )
    }

    @Transactional
    fun registerInterestCategories(onboardingInfo: OnboardingInfo) {
        memberInterestCategoryRepository.saveAll(
            onboardingInfo.interestCategoryIds.map {
                MemberInterestCategoryEntity.new(
                    interestCategoryId = it,
                    memberKey = onboardingInfo.memberKey,
                )
            },
        )
    }

    @Transactional
    fun registerFavoriteAreas(memberDetailInfo: OnboardingInfo) {
        memberFavoriteAreaRepository.saveAll(
            memberDetailInfo.favoriteAreaIds.map {
                MemberFavoriteAreaEntity.new(
                    favoriteAreaId = it,
                    memberKey = memberDetailInfo.memberKey,
                )
            },
        )
    }
}
