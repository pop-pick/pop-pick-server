package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.MemberDetailEntity
import com.poppick.poppick.feature.member.dataaccess.repository.custom.CustomMemberDetailRepository
import org.springframework.data.jpa.repository.JpaRepository

interface MemberDetailRepository :
    JpaRepository<MemberDetailEntity, Long>,
    CustomMemberDetailRepository
