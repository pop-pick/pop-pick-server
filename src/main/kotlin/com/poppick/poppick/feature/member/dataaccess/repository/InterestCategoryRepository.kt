package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.InterestCategoryEntity
import org.springframework.data.jpa.repository.JpaRepository

interface InterestCategoryRepository : JpaRepository<InterestCategoryEntity, Int>
