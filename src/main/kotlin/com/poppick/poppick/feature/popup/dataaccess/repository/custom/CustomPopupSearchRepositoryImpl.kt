package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.QPopupEntity.popupEntity
import com.poppick.poppick.feature.popup.domain.PopupSearchCursor
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport
import com.querydsl.core.types.dsl.BooleanExpression
import java.time.LocalDate

class CustomPopupSearchRepositoryImpl :
    QuerydslRepositorySupport(PopupEntity::class),
    CustomPopupSearchRepository {
    override fun findPopups(
        keyword: String?,
        today: LocalDate,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupEntity> {
        val content =
            selectFrom(popupEntity)
                .where(
                    popupEntity.endDate.isNull.or(popupEntity.endDate.goe(today)),
                    popupEntity.startDate.isNull.or(popupEntity.startDate.loe(today)),
                    containsKeyword(keyword),
                    cursorable.cursor?.let { afterCursor(it) },
                ).orderBy(popupEntity.startDate.desc().nullsLast(), popupEntity.id.desc())
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

    /**
     * (start_date DESC NULLS LAST, popup_id DESC) 순서에서 cursor 값 다음에 오는 행.
     * - cursor start_date 가 있으면: 더 이른 오픈일 · 같은 오픈일의 더 작은 popup_id · 오픈일 없는 구간 전체
     * - cursor start_date 가 NULL 이면: 오픈일 없는 구간에서 더 작은 popup_id
     */
    private fun afterCursor(cursor: PopupSearchCursor): BooleanExpression {
        val sameStartDateAfter = popupEntity.id.lt(cursor.popupId)
        val cursorStartDate = cursor.startDate ?: return popupEntity.startDate.isNull.and(sameStartDateAfter)

        return popupEntity.startDate
            .lt(cursorStartDate)
            .or(popupEntity.startDate.eq(cursorStartDate).and(sameStartDateAfter))
            .or(popupEntity.startDate.isNull)
    }
}
