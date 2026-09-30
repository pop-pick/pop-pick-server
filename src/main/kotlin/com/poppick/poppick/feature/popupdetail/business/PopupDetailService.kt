package com.poppick.poppick.feature.popupdetail.business

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popupdetail.domain.PopupViewer
import com.poppick.poppick.feature.popupdetail.implement.PopupDetailReader
import com.poppick.poppick.feature.popupdetail.implement.PopupViewCounter
import org.springframework.stereotype.Service

@Service
class PopupDetailService(
    private val popupDetailReader: PopupDetailReader,
    private val popupViewCounter: PopupViewCounter,
) {
    /**
     * 팝업을 읽은 뒤(없으면 NOT_FOUND_DATA, 조회수 증가 없음) 조회수를 반영한다.
     * 새 조회면 올린 뒤 값을, 중복 조회 · Redis/DB 오류면 읽은 값을 그대로 담는다(조회수 반영 실패로 상세 조회가 실패하지 않는다).
     * Redis 호출을 DB 트랜잭션 밖에 두기 위해 트랜잭션을 걸지 않는다(증가 트랜잭션은 PopupViewCountWriter 에만 있다).
     */
    fun findPopupDetail(
        popupId: Long,
        viewer: PopupViewer,
    ): Popup {
        val popup = popupDetailReader.read(popupId)
        val viewCount = popupViewCounter.count(popupId, viewer) ?: return popup
        return popup.copy(viewCount = viewCount)
    }
}
