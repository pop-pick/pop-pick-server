package com.poppick.poppick.feature.wish.dataaccess.repository.custom

import com.poppick.poppick.feature.wish.dataaccess.entity.PopupWishEntity
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice

interface CustomPopupWishRepository {
    /** 회원의 찜을 popup_wish_id 내림차순(최근 찜한 순)으로. cursor 는 이전 페이지 마지막 popup_wish_id. */
    fun findByMemberKey(
        memberKey: String,
        cursorable: Cursorable<Long>,
    ): Slice<PopupWishEntity>

    /** popupIds 중 회원이 찜한 id. IN 한 번. */
    fun findWishedPopupIds(
        memberKey: String,
        popupIds: Collection<Long>,
    ): Set<Long>

    /** 회원의 해당 팝업 찜을 지우고 삭제 건수(0 또는 1)를 돌려준다(bulk). */
    fun deleteByMemberKeyAndPopupId(
        memberKey: String,
        popupId: Long,
    ): Long
}
