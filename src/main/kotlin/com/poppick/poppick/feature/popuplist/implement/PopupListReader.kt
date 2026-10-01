package com.poppick.poppick.feature.popuplist.implement

import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.feature.popup.implement.PopupSearchReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PopupListReader(
    private val popupSearchReader: PopupSearchReader,
) {
    fun findPopups(
        keyword: String?,
        today: LocalDate,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<Popup> = popupSearchReader.findPopups(keyword, today, sort, cursorable)

    fun findMapPopups(
        keyword: String?,
        today: LocalDate,
        bounds: MapBounds,
        limit: Int,
    ): List<Popup> = popupSearchReader.findMapPopups(keyword, today, bounds, limit)
}
