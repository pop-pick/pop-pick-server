package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import java.time.LocalDate

interface CustomPopupSearchRepository {
    /**
     * 노출 중인 팝업(오픈일 최신순: start_date DESC NULLS LAST, popup_id DESC).
     * end_date IS NULL OR end_date >= today, start_date IS NULL OR start_date <= today,
     * keyword 는 title · brand · address_road · address_jibun 부분 일치(대소문자 무시).
     * cursor 는 이전 페이지 마지막 팝업의 (start_date, popup_id) 이며, 그 값 다음부터 조회한다.
     * cursor 팝업 row 를 다시 조회하지 않으므로 그 row 가 삭제됐어도 이어서 조회된다.
     */
    fun findPopups(
        keyword: String?,
        today: LocalDate,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupEntity>
}
