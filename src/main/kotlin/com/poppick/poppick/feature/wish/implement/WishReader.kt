package com.poppick.poppick.feature.wish.implement

import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.feature.wish.dataaccess.repository.PopupWishRepository
import com.poppick.poppick.feature.wish.domain.WishedPopup
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class WishReader(
    private val popupWishRepository: PopupWishRepository,
    private val popupReader: PopupReader,
) {
    /**
     * 내 찜 목록 한 페이지(최근 찜한 순). 종료된 팝업도 포함한다.
     * 찜을 먼저 읽고 팝업은 IN 한 번으로 붙인다(N+1 없음). 그 사이 팝업이 지워졌으면 그 항목은 빠진다.
     */
    fun findWishedPopups(
        memberKey: String,
        today: LocalDate,
        cursorable: Cursorable<Long>,
    ): Slice<WishedPopup> {
        val wishes = popupWishRepository.findByMemberKey(memberKey, cursorable).map { it.toDomain() }
        val popups = popupReader.findAllByIds(wishes.content.map { it.popupId }).associateBy { it.id }
        val content = wishes.content.mapNotNull { wish -> popups[wish.popupId]?.let { WishedPopup.of(wish, it, today) } }
        return Slice(content, cursorable, wishes.hasNext)
    }

    /** popupIds 중 회원이 찜한 id. 비어 있으면 조회하지 않는다. */
    fun findWishedPopupIds(
        memberKey: String,
        popupIds: Collection<Long>,
    ): Set<Long> = if (popupIds.isEmpty()) emptySet() else popupWishRepository.findWishedPopupIds(memberKey, popupIds)
}
