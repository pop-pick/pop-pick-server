package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.repository.PopupViewCountRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PopupViewCountWriter(
    private val popupViewCountRepository: PopupViewCountRepository,
) {
    /**
     * 조회수를 1 올리고 올린 뒤 값을 반환한다. 팝업이 없으면(그 사이 삭제 등) NULL.
     * UPDATE 가 잡은 행 락은 커밋까지 유지되므로 같은 트랜잭션의 조회 값은 이번 증가분이 반영된 값이다.
     */
    @Transactional
    fun increase(popupId: Long): Long? {
        if (popupViewCountRepository.increaseViewCount(popupId) == 0) return null
        return popupViewCountRepository.findViewCount(popupId)
    }
}
