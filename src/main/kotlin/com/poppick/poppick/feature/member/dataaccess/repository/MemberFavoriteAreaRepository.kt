package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.MemberFavoriteAreaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface MemberFavoriteAreaRepository : JpaRepository<MemberFavoriteAreaEntity, Long>
