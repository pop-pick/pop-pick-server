package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import com.poppick.poppick.feature.wish.implement.WishReader
import org.springframework.stereotype.Service

@Service
class PopupDetailService(
    private val popupDetailReader: PopupDetailReader,
    private val wishReader: WishReader,
) {
    /** memberKey 가 null(비로그인)이면 찜을 조회하지 않고 wished = false. */
    fun findPopupDetail(
        memberKey: String?,
        popupId: Long,
    ): PopupDetail {
        val popup = popupDetailReader.read(popupId)
        val wished = memberKey?.let { popupId in wishReader.findWishedPopupIds(it, listOf(popupId)) } ?: false
        return PopupDetail(popup, wished)
    }
}
