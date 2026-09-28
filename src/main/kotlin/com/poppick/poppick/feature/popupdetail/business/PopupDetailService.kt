package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import org.springframework.stereotype.Service

@Service
class PopupDetailService(
    private val popupDetailReader: PopupDetailReader,
) {
    fun findPopupDetail(popupId: Long) = popupDetailReader.read(popupId)
}
