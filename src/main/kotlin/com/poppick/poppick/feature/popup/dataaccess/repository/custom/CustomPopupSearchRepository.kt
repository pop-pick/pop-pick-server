package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.domain.MapBounds
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import java.time.LocalDate

interface CustomPopupSearchRepository {
    /**
     * 노출 중인 팝업.
     * - LATEST(오픈일 최신순): start_date DESC NULLS LAST, popup_id DESC
     * - POPULAR(인기순): view_count DESC, popup_id DESC
     * end_date IS NULL OR end_date >= today, start_date IS NULL OR start_date <= today,
     * keyword 는 title · brand · address_road · address_jibun 부분 일치(대소문자 무시) OR area_id IN keywordAreaIds.
     * keywordAreaIds 는 이름이 keyword 와 부분 일치하는 상권 id 로, 호출 측이 구해 넘긴다(keyword 가 없으면 쓰지 않는다).
     * areaId 가 있으면 area_id = areaId 인 팝업만(keyword 조건과 AND).
     * cursor 는 이전 페이지 마지막 팝업의 정렬 기준 값(LATEST: start_date, popup_id / POPULAR: view_count, popup_id)이며,
     * 그 값 다음부터 조회한다. cursor 정렬은 sort 와 같아야 한다.
     * cursor 팝업 row 를 다시 조회하지 않으므로 그 row 가 삭제됐어도 이어서 조회된다.
     */
    fun findPopups(
        keyword: String?,
        keywordAreaIds: Collection<Int>,
        areaId: Int?,
        today: LocalDate,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupEntity>

    /**
     * 지도 영역 안의 노출 중인 팝업(최대 limit 건). 노출 조건 · keyword 조건(keywordAreaIds 포함)은 findPopups 와 같다.
     * 좌표가 있고 latitude BETWEEN swLat AND neLat, longitude BETWEEN swLng AND neLng(경계 포함)인 팝업만.
     * 상한에 걸리면 인기 팝업이 남도록 인기순(view_count DESC, popup_id DESC)으로 자른다.
     */
    fun findMapPopups(
        keyword: String?,
        keywordAreaIds: Collection<Int>,
        today: LocalDate,
        bounds: MapBounds,
        limit: Int,
    ): List<PopupEntity>

    /**
     * 회원 취향과 맞는 노출 중인 팝업(최대 limit 건). 노출 조건은 findPopups 와 같다.
     * interest_category_id IN categoryIds OR area_id IN areaIds 이며, 빈 목록 쪽 조건은 뺀다(NULL 은 어느 쪽과도 일치하지 않는다).
     * 정렬은 인기순(view_count DESC, popup_id DESC). 두 목록이 모두 비면 호출하지 않는다.
     */
    fun findPreferredPopups(
        categoryIds: Collection<Int>,
        areaIds: Collection<Int>,
        today: LocalDate,
        limit: Int,
    ): List<PopupEntity>
}
