package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.util.KST
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PopupListService(
    private val popupListReader: PopupListReader,
) {
    fun findPopups(
        keyword: String?,
        cursorable: Cursorable<PopupSearchCursor>,
    ) = popupListReader.findPopups(keyword, LocalDate.now(KST), cursorable)
}
