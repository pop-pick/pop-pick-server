package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.MemberInterestCategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MemberInterestCategoryRepository : JpaRepository<MemberInterestCategoryEntity, Long>
