package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.member.implement.FavoriteAreaReader
import com.poppick.poppick.feature.member.implement.InterestCategoryReader
import com.poppick.poppick.feature.popupdetail.domain.PopupDetail
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import com.poppick.poppick.feature.popupdetail.implement.PopupViewCounter
import com.poppick.poppick.feature.wish.implement.WishReader
import org.springframework.stereotype.Service

@Service
class PopupDetailService(
    private val popupDetailReader: PopupDetailReader,
    private val popupViewCounter: PopupViewCounter,
    private val interestCategoryReader: InterestCategoryReader,
    private val favoriteAreaReader: FavoriteAreaReader,
    private val wishReader: WishReader,
) {
    /**
     * 팝업을 읽은 뒤(없으면 NOT_FOUND_DATA, 조회수 증가 없음) 조회수를 반영하고 찜 여부를 붙인다.
     * 새 조회면 올린 뒤 값을, 중복 조회 · Redis/DB 오류면 읽은 값을 그대로 담는다(조회수 반영 실패로 상세 조회가 실패하지 않는다).
     * memberKey 가 null(비로그인)이면 찜을 조회하지 않고 wished = false.
     * Redis 호출을 DB 트랜잭션 밖에 두기 위해 트랜잭션을 걸지 않는다(증가 트랜잭션은 PopupViewCountWriter 에만 있다).
     */
    fun findPopupDetail(
        memberKey: String?,
        popupId: Long,
        viewer: PopupViewer,
    ): PopupDetail {
        val popup = popupDetailReader.read(popupId)
        val counted = popupViewCounter.count(popupId, viewer)?.let { popup.copy(viewCount = it) } ?: popup
        val wished = memberKey?.let { popupId in wishReader.findWishedPopupIds(it, listOf(popupId)) } ?: false
        return PopupDetail(counted, wished)
    }

    /** 카테고리 id → 이름. 상세 뱃지 표시용. */
    fun findCategoryNames(): Map<Int, String> = interestCategoryReader.findAll().associate { it.id to it.category }

    /** 상권 id → 이름. 상세 지역 뱃지 표시용. */
    fun findAreaNames(): Map<Int, String> = favoriteAreaReader.findAll().associate { it.id to it.area }
}
