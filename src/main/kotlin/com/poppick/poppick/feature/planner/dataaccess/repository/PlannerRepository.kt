package com.poppick.poppick.feature.planner.dataaccess.repository

import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.repository.custom.CustomPlannerRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PlannerRepository :
    JpaRepository<PlannerEntity, Long>,
    CustomPlannerRepository
