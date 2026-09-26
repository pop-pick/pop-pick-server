package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.MemberEntity
import com.poppick.poppick.feature.member.dataaccess.repository.custom.CustomMemberRepository
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository :
    JpaRepository<MemberEntity, Long>,
    CustomMemberRepository
