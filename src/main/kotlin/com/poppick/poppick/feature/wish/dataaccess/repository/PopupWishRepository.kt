package com.poppick.poppick.feature.wish.dataaccess.repository

import com.poppick.poppick.feature.wish.dataaccess.entity.PopupWishEntity
import com.poppick.poppick.feature.wish.dataaccess.repository.custom.CustomPopupWishRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface PopupWishRepository :
    JpaRepository<PopupWishEntity, Long>,
    CustomPopupWishRepository {
    /**
     * 찜 1건 저장. 이미 있으면 아무것도 하지 않는다(동시 요청에도 unique 위반 없이 멱등).
     * 영향 행 수(새로 저장 1 · 이미 있음 0)를 돌려준다. 트랜잭션 안에서 불러야 한다.
     */
    @Modifying
    @Query(
        value =
            "INSERT INTO popup_wish (member_key, popup_id, created_at) VALUES (:memberKey, :popupId, :createdAt) " +
                "ON CONFLICT (member_key, popup_id) DO NOTHING",
        nativeQuery = true,
    )
    fun insertIgnoringDuplicate(
        @Param("memberKey") memberKey: String,
        @Param("popupId") popupId: Long,
        @Param("createdAt") createdAt: OffsetDateTime,
    ): Int
}
