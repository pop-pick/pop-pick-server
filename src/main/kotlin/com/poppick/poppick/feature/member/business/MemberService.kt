package com.poppick.poppick.feature.member.business

import com.poppick.poppick.feature.member.domain.OnboardingInfo
import com.poppick.poppick.feature.member.implement.MemberFinder
import com.poppick.poppick.feature.member.implement.MemberRegistrar
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.stereotype.Service

@Service
class MemberService(
    private val memberFinder: MemberFinder,
    private val memberRegistrar: MemberRegistrar,
) {
    fun registerOnBoardingInfo(onboardingInfo: OnboardingInfo) {
        val member = memberFinder.find(onboardingInfo.memberKey)

        if (memberFinder.existsMemberDetail(member.memberKey)) {
            throw AppException(ErrorType.ALREADY_REGISTERED)
        }

        memberRegistrar.registerDetails(onboardingInfo)
    }
}
