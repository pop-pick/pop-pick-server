package com.poppick.poppick.feature.member.dataaccess.repository.custom

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity

interface CustomMemberRepository {
    fun findByMemberKey(memberKey: String): MemberEntity?
}
