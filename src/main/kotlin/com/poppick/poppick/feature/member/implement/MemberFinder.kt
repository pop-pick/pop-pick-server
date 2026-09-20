package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.MemberDetailRepository
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.stereotype.Component

@Component
class MemberFinder(
    private val memberRepository: MemberRepository,
    private val memberDetailRepository: MemberDetailRepository,
) {
    fun find(memberKey: String) = memberRepository.findByMemberKey(memberKey)?.toDomain() ?: throw AppException(ErrorType.NOT_FOUND_DATA)

    fun existsMemberDetail(memberKey: String) = memberDetailRepository.existsMemberDetail(memberKey)
}
