package com.poppick.poppick.feature.wish.dataaccess.repository.custom

import com.poppick.poppick.feature.wish.dataaccess.entity.PopupWishEntity
import com.poppick.poppick.feature.wish.dataaccess.entity.QPopupWishEntity.popupWishEntity
import com.poppick.poppick.global.paging.Cursorable
import com.poppick.poppick.global.paging.Slice
import com.poppick.poppick.global.querydsl.QuerydslRepositorySupport

class CustomPopupWishRepositoryImpl :
    QuerydslRepositorySupport(PopupWishEntity::class),
    CustomPopupWishRepository {
    // idx_popup_wish_member(member_key, popup_wish_id desc) 를 탄다.
    override fun findByMemberKey(
        memberKey: String,
        cursorable: Cursorable<Long>,
    ): Slice<PopupWishEntity> {
        val content =
            selectFrom(popupWishEntity)
                .where(
                    popupWishEntity.memberKey.eq(memberKey),
                    cursorable.cursor?.let { popupWishEntity.id.lt(it) },
                ).orderBy(popupWishEntity.id.desc())
                .limit(cursorable.limit + 1L)
                .fetch()
        return Slice(content, cursorable, hasNext(cursorable, content))
    }

    override fun findWishedPopupIds(
        memberKey: String,
        popupIds: Collection<Long>,
    ): Set<Long> {
        if (popupIds.isEmpty()) return emptySet()
        return select(popupWishEntity.popupId)
            .from(popupWishEntity)
            .where(popupWishEntity.memberKey.eq(memberKey), popupWishEntity.popupId.`in`(popupIds))
            .fetch()
            .toSet()
    }

    override fun deleteByMemberKeyAndPopupId(
        memberKey: String,
        popupId: Long,
    ): Long =
        delete(popupWishEntity)
            .where(popupWishEntity.memberKey.eq(memberKey), popupWishEntity.popupId.eq(popupId))
            .execute()
}
