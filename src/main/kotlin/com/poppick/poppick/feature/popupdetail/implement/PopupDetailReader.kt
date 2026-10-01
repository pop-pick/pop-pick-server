package com.poppick.poppick.feature.popupdetail.implement

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.implement.PopupReader
import org.springframework.stereotype.Component

@Component
class PopupDetailReader(
    private val popupReader: PopupReader,
) {
    fun read(popupId: Long): Popup = popupReader.findById(popupId)
}
