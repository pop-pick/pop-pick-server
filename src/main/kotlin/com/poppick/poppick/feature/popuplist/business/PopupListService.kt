package com.poppick.poppick.feature.popuplist.business

import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popuplist.domain.PopupListItem
import com.poppick.poppick.feature.popuplist.implement.PopupListReader
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class PopupListService(
    private val popupListReader: PopupListReader,
    private val wishReader: WishReader,
) {
    /** memberKey 가 null(비로그인)이면 찜을 조회하지 않고 전부 wished = false. */
    fun findPopups(
        memberKey: String?,
        keyword: String?,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupListItem> {
        val slice = popupListReader.findPopups(keyword, LocalDate.now(KST), cursorable)
        val wishedIds = memberKey?.let { wishReader.findWishedPopupIds(it, slice.content.mapNotNull { popup -> popup.id }) }.orEmpty()
        return slice.map { PopupListItem(it, it.id in wishedIds) }
    }
}
