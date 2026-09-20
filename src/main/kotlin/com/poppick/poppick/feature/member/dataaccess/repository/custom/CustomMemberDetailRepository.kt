package com.poppick.poppick.feature.member.dataaccess.repository.custom

interface CustomMemberDetailRepository {
    fun existsMemberDetail(memberKey: String): Boolean
}
