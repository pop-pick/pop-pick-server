package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.custom.CustomPopupSearchRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PopupSearchRepository :
    JpaRepository<PopupEntity, Long>,
    CustomPopupSearchRepository
