package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupSearchRepository
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PopupSearchReader(
    private val popupSearchRepository: PopupSearchRepository,
) {
    fun findPopups(
        keyword: String?,
        today: LocalDate,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<Popup> = popupSearchRepository.findPopups(keyword, today, cursorable).map { it.toDomain() }
}
