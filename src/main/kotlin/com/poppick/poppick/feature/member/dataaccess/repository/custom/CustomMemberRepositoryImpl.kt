package com.poppick.poppick.feature.member.dataaccess.repository.custom

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.entity.QMemberEntity.memberEntity
import com.poppick.poppick.global.entity.Status
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport

class CustomMemberRepositoryImpl :
    QuerydslRepositorySupport(MemberEntity::class),
    CustomMemberRepository {
    override fun findByMemberKey(memberKey: String) =
        selectFrom(memberEntity)
            .where(
                memberEntity.memberKey.eq(memberKey),
                memberEntity.status().status.eq(Status.ACTIVE),
            ).fetchOne()
}
