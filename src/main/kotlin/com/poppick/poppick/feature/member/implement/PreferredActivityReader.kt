package com.poppick.poppick.feature.member.implement

import com.poppick.poppick.feature.member.dataaccess.repository.PreferredActivityRepository
import com.poppick.poppick.feature.member.domain.PreferredActivity
import org.springframework.stereotype.Component

@Component
class PreferredActivityReader(
    private val preferredActivityRepository: PreferredActivityRepository,
) {
    fun findAll(): List<PreferredActivity> = preferredActivityRepository.findAll().map { it.toPreferredActivity() }
}
