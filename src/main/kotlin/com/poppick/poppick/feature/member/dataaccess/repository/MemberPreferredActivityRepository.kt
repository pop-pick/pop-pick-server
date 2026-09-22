package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.MemberPreferredActivityEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MemberPreferredActivityRepository : JpaRepository<MemberPreferredActivityEntity, Long>
