package com.poppick.poppick.feature.wish.implement

import com.poppick.poppick.feature.wish.dataaccess.repository.PopupWishRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class WishWriter(
    private val popupWishRepository: PopupWishRepository,
) {
    /** 찜 저장(멱등). 새로 저장했으면 1, 이미 있었으면 0. 팝업 존재 확인은 호출 측이 한다. */
    @Transactional
    fun add(
        memberKey: String,
        popupId: Long,
        now: OffsetDateTime,
    ): Int = popupWishRepository.insertIgnoringDuplicate(memberKey, popupId, now)

    /** 찜 해제(멱등). 삭제 건수(0 또는 1). */
    @Transactional
    fun remove(
        memberKey: String,
        popupId: Long,
    ): Long = popupWishRepository.deleteByMemberKeyAndPopupId(memberKey, popupId)
}
