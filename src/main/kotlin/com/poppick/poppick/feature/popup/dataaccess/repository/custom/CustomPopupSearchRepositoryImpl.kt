package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.QPopupEntity.popupEntity
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.feature.popup.domain.PopupSortType
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import java.time.LocalDate

class CustomPopupSearchRepositoryImpl :
    QuerydslRepositorySupport(PopupEntity::class),
    CustomPopupSearchRepository {
    override fun findPopups(
        keyword: String?,
        today: LocalDate,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupEntity> {
        val content =
            selectFrom(popupEntity)
                .where(
                    popupEntity.endDate.isNull.or(popupEntity.endDate.goe(today)),
                    popupEntity.startDate.isNull.or(popupEntity.startDate.loe(today)),
                    containsKeyword(keyword),
                    cursorable.cursor?.let { afterCursor(sort, it) },
                ).orderBy(*orderOf(sort))
                .limit(cursorable.limit + 1L)
                .fetch()
        val hasNext = hasNext(cursorable, content)

        return Slice(content, cursorable, hasNext)
    }

    private fun containsKeyword(keyword: String?): BooleanExpression? =
        keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
            popupEntity.title
                .containsIgnoreCase(it)
                .or(popupEntity.brand.containsIgnoreCase(it))
                .or(popupEntity.addressRoad.containsIgnoreCase(it))
                .or(popupEntity.addressJibun.containsIgnoreCase(it))
        }

    private fun orderOf(sort: PopupSortType): Array<OrderSpecifier<*>> =
        when (sort) {
            PopupSortType.LATEST -> arrayOf(popupEntity.startDate.desc().nullsLast(), popupEntity.id.desc())
            PopupSortType.POPULAR -> arrayOf(popupEntity.viewCount.desc(), popupEntity.id.desc())
        }

    private fun afterCursor(
        sort: PopupSortType,
        cursor: PopupSearchCursor,
    ): BooleanExpression {
        require(cursor.sort == sort) { "cursor 정렬(${cursor.sort})이 요청 정렬($sort)과 다르다" }
        return when (cursor) {
            is PopupSearchCursor.Latest -> afterLatestCursor(cursor)
            is PopupSearchCursor.Popular -> afterPopularCursor(cursor)
        }
    }

    /**
     * (start_date DESC NULLS LAST, popup_id DESC) 순서에서 cursor 값 다음에 오는 행.
     * - cursor start_date 가 있으면: 더 이른 오픈일 · 같은 오픈일의 더 작은 popup_id · 오픈일 없는 구간 전체
     * - cursor start_date 가 NULL 이면: 오픈일 없는 구간에서 더 작은 popup_id
     */
    private fun afterLatestCursor(cursor: PopupSearchCursor.Latest): BooleanExpression {
        val sameStartDateAfter = popupEntity.id.lt(cursor.popupId)
        val cursorStartDate = cursor.startDate ?: return popupEntity.startDate.isNull.and(sameStartDateAfter)

        return popupEntity.startDate
            .lt(cursorStartDate)
            .or(popupEntity.startDate.eq(cursorStartDate).and(sameStartDateAfter))
            .or(popupEntity.startDate.isNull)
    }

    /** (view_count DESC, popup_id DESC) 순서에서 cursor 값 다음에 오는 행: 더 작은 조회수 · 같은 조회수의 더 작은 popup_id. */
    private fun afterPopularCursor(cursor: PopupSearchCursor.Popular): BooleanExpression =
        popupEntity.viewCount
            .lt(cursor.viewCount)
            .or(popupEntity.viewCount.eq(cursor.viewCount).and(popupEntity.id.lt(cursor.popupId)))
}
