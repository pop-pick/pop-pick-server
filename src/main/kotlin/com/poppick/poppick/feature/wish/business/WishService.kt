package com.poppick.poppick.feature.wish.business

import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.feature.wish.domain.WishedPopup
import com.poppick.poppick.feature.wish.implement.WishReader
import com.poppick.poppick.feature.wish.implement.WishWriter
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.util.KST
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class WishService(
    private val wishReader: WishReader,
    private val wishWriter: WishWriter,
    private val popupReader: PopupReader,
    private val clock: Clock = Clock.system(KST),
) {
    /** 찜 등록(멱등). 없는 팝업이면 NOT_FOUND_DATA. 종료된 팝업도 찜할 수 있다. */
    fun wish(
        memberKey: String,
        popupId: Long,
    ) {
        popupReader.findById(popupId)
        wishWriter.add(memberKey, popupId, OffsetDateTime.now(clock))
    }

    /** 찜 해제(멱등). 팝업 존재 여부는 보지 않는다. */
    fun unwish(
        memberKey: String,
        popupId: Long,
    ) {
        wishWriter.remove(memberKey, popupId)
    }

    /** 내 찜 목록(최근 찜한 순, 종료 팝업 포함). */
    fun findWishes(
        memberKey: String,
        cursorable: Cursorable<Long>,
    ): Slice<WishedPopup> = wishReader.findWishedPopups(memberKey, LocalDate.now(clock), cursorable)
}
