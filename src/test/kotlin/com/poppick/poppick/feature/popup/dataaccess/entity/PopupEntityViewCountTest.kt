package com.poppick.poppick.feature.popup.dataaccess.entity

import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.Column

class PopupEntityViewCountTest :
    FunSpec({
        test("조회수 기본값은 0") {
            Popup(source = SourceType.KAKAO_MAP, title = "팝업").viewCount shouldBe 0L
            PopupEntity(source = SourceType.KAKAO_MAP, title = "팝업").viewCount shouldBe 0L
        }

        test("from · toDomain 이 viewCount 를 그대로 옮긴다") {
            val popup = Popup(source = SourceType.KAKAO_MAP, title = "팝업", viewCount = 12_000L, id = 1L)

            val entity = PopupEntity.from(popup)

            entity.viewCount shouldBe 12_000L
            entity.toDomain() shouldBe popup
        }

        test("view_count 컬럼은 JPA INSERT/UPDATE 에서 제외된다(보강 저장이 덮어쓰지 않음)") {
            val column = PopupEntity::class.java.getDeclaredField("viewCount").getAnnotation(Column::class.java)

            column.name shouldBe "view_count"
            column.nullable shouldBe false
            column.insertable shouldBe false
            column.updatable shouldBe false
        }
    })
