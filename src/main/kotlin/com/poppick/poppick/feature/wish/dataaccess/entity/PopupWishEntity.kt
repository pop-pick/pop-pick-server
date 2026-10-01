package com.poppick.poppick.feature.wish.dataaccess.entity

import com.poppick.poppick.feature.wish.domain.PopupWish
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * member · popup 은 다른 feature 라 연관관계 대신 평문 컬럼(FK 는 DDL, ON DELETE CASCADE).
 * 저장은 ON CONFLICT 네이티브 쿼리로만 한다(PopupWishRepository.insertIgnoringDuplicate).
 */
@Entity
@Table(name = "popup_wish")
class PopupWishEntity(
    @Column(nullable = false)
    var memberKey: String,
    @Column(nullable = false)
    var popupId: Long,
    @Column(nullable = false, updatable = false)
    var createdAt: OffsetDateTime,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "popup_wish_id")
    var id: Long? = null,
) {
    fun toDomain() = PopupWish(id = id!!, memberKey = memberKey, popupId = popupId, createdAt = createdAt)
}
