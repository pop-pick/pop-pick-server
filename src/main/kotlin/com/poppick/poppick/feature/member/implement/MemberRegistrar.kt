package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.MemberRepository
import com.poppick.poppick.feature.member.domain.NewMember
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional
@Component
class MemberRegistrar(
    private val memberRepository: MemberRepository,
) {
    fun register(newMember: NewMember) = memberRepository.save(MemberEntity.from(newMember)).memberKey
}
