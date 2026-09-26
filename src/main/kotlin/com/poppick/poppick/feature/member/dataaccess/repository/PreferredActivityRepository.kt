package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.PreferredActivityEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PreferredActivityRepository : JpaRepository<PreferredActivityEntity, Int>
