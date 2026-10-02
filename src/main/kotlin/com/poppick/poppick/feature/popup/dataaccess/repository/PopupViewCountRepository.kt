package com.poppick.poppick.feature.popup.dataaccess.repository

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface PopupViewCountRepository : JpaRepository<PopupEntity, Long> {
    /**
     * 조회수를 DB 에서 원자적으로 1 올린다(읽고 더해 저장하지 않는다). 갱신 건수를 반환하며, 팝업이 없으면 0.
     * view_count 는 엔티티에서 updatable = false 라 JPQL 이 아닌 native UPDATE 로 바꾼다.
     */
    @Modifying
    @Query("UPDATE popup SET view_count = view_count + 1 WHERE popup_id = :popupId", nativeQuery = true)
    fun increaseViewCount(popupId: Long): Int

    /** 조회수만 스칼라로 조회한다(영속성 컨텍스트의 엔티티를 거치지 않는다). 팝업이 없으면 NULL. */
    @Query("SELECT p.viewCount FROM PopupEntity p WHERE p.id = :popupId")
    fun findViewCount(popupId: Long): Long?
}
