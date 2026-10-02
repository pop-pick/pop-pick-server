package com.poppick.poppick.feature.popup.dataaccess.repository.custom

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.QPopupEntity.popupEntity
import com.poppick.poppick.feature.popup.domain.MapBounds
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
        keywordAreaIds: Collection<Int>,
        areaId: Int?,
        today: LocalDate,
        sort: PopupSortType,
        cursorable: Cursorable<PopupSearchCursor>,
    ): Slice<PopupEntity> {
        val content =
            selectFrom(popupEntity)
                .where(
                    visible(today),
                    containsKeyword(keyword, keywordAreaIds),
                    areaIs(areaId),
                    cursorable.cursor?.let { afterCursor(sort, it) },
                ).orderBy(*orderOf(sort))
                .limit(cursorable.limit + 1L)
                .fetch()
        val hasNext = hasNext(cursorable, content)

        return Slice(content, cursorable, hasNext)
    }

    override fun findMapPopups(
        keyword: String?,
        keywordAreaIds: Collection<Int>,
        today: LocalDate,
        bounds: MapBounds,
        limit: Int,
    ): List<PopupEntity> =
        selectFrom(popupEntity)
            .where(
                visible(today),
                containsKeyword(keyword, keywordAreaIds),
                within(bounds),
            ).orderBy(*orderOf(PopupSortType.POPULAR))
            .limit(limit.toLong())
            .fetch()

    override fun findPreferredPopups(
        categoryIds: Collection<Int>,
        areaIds: Collection<Int>,
        today: LocalDate,
        limit: Int,
    ): List<PopupEntity> {
        val preferred = matchesPreference(categoryIds, areaIds)
        require(preferred != null) { "관심 카테고리 · 선호 지역이 모두 비어 있다" }

        return selectFrom(popupEntity)
            .where(
                visible(today),
                preferred,
            ).orderBy(*orderOf(PopupSortType.POPULAR))
            .limit(limit.toLong())
            .fetch()
    }

    /** 관심 카테고리 일치 OR 선호 지역 일치. 빈 목록 쪽 조건은 빼고, 둘 다 비면 null. */
    private fun matchesPreference(
        categoryIds: Collection<Int>,
        areaIds: Collection<Int>,
    ): BooleanExpression? =
        listOfNotNull(
            categoryIds.takeIf { it.isNotEmpty() }?.let { popupEntity.interestCategoryId.`in`(it) },
            areaIds.takeIf { it.isNotEmpty() }?.let { popupEntity.areaId.`in`(it) },
        ).reduceOrNull(BooleanExpression::or)

    /** 노출 중: 종료되지 않았고(end_date IS NULL OR end_date >= today) 오픈했다(start_date IS NULL OR start_date <= today). */
    private fun visible(today: LocalDate): BooleanExpression =
        popupEntity.endDate.isNull
            .or(popupEntity.endDate.goe(today))
            .and(popupEntity.startDate.isNull.or(popupEntity.startDate.loe(today)))

    /** 좌표가 있고 지도 영역 안(경계 포함). */
    private fun within(bounds: MapBounds): BooleanExpression =
        popupEntity.latitude.isNotNull
            .and(popupEntity.longitude.isNotNull)
            .and(popupEntity.latitude.between(bounds.swLat, bounds.neLat))
            .and(popupEntity.longitude.between(bounds.swLng, bounds.neLng))

    /** 이름 · 브랜드 · 주소 부분 일치 OR 이름이 keyword 와 맞는 상권(keywordAreaIds). keyword 가 없으면 조건 없음. */
    private fun containsKeyword(
        keyword: String?,
        keywordAreaIds: Collection<Int>,
    ): BooleanExpression? =
        keyword?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val text =
                popupEntity.title
                    .containsIgnoreCase(it)
                    .or(popupEntity.brand.containsIgnoreCase(it))
                    .or(popupEntity.addressRoad.containsIgnoreCase(it))
                    .or(popupEntity.addressJibun.containsIgnoreCase(it))
            if (keywordAreaIds.isEmpty()) text else text.or(popupEntity.areaId.`in`(keywordAreaIds))
        }

    /** 목록 지역 필터. areaId 가 없으면 조건 없음. */
    private fun areaIs(areaId: Int?): BooleanExpression? = areaId?.let { popupEntity.areaId.eq(it) }

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
