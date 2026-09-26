package com.poppick.poppick.feature.member.dataaccess.repository.custom

import com.poppick.poppick.feature.member.dataaccess.entity.MemberDetailEntity
import com.poppick.poppick.feature.member.dataaccess.entity.QMemberDetailEntity.memberDetailEntity
import com.poppick.poppick.global.entity.Status
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import org.springframework.stereotype.Repository

@Repository
class CustomMemberDetailRepositoryImpl :
    QuerydslRepositorySupport(MemberDetailEntity::class),
    CustomMemberDetailRepository {
    override fun existsMemberDetail(memberKey: String) =
        selectOne()
            .from(memberDetailEntity)
            .where(
                memberDetailEntity.memberKey.eq(memberKey),
                memberDetailEntity.status().status.eq(Status.ACTIVE),
            ).fetchFirst() != null
}
