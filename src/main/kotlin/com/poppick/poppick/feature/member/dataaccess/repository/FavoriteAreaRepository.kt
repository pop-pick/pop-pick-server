package com.poppick.poppick.feature.member.dataaccess.repository

import com.poppick.poppick.feature.member.dataaccess.entity.FavoriteAreaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface FavoriteAreaRepository : JpaRepository<FavoriteAreaEntity, Int>
