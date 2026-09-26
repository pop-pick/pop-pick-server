package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.custom.CustomPopupRepository
import org.springframework.data.jpa.repository.JpaRepository

interface PopupRepository :
    JpaRepository<PopupEntity, Long>,
    CustomPopupRepository
