package com.poppick.poppick.feature.planner.implement

import com.poppick.poppick.feature.planner.PlannerFixtures
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.planner.dataaccess.repository.PlannerRepository
import com.poppick.poppick.feature.planner.domain.PlannerStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder

class PlannerWriterTest :
    FunSpec({
        test("saveDraft: 회원의 기존 DRAFT 를 먼저 지우고 새 DRAFT 를 저장한다") {
            val repository = mockk<PlannerRepository>()
            val saved = slot<PlannerEntity>()
            every { repository.deleteDrafts("member-1") } returns 2
            every { repository.save(capture(saved)) } answers { saved.captured.apply { id = 99 } }

            val planner = PlannerWriter(repository).saveDraft(PlannerFixtures.planner(id = null))

            planner.id shouldBe 99
            planner.status shouldBe PlannerStatus.DRAFT
            verifyOrder {
                repository.deleteDrafts("member-1")
                repository.save(any())
            }
            // SCHEDULED · CANCELED 는 DRAFT 조건 삭제(deleteDrafts) 에만 맡기고 다른 삭제는 부르지 않는다.
            verify(exactly = 0) { repository.deleteById(any()) }
            verify(exactly = 0) { repository.deleteAll(any()) }
        }

        test("purgeDrafts: 기준 시각을 그대로 넘기고 삭제 건수를 돌려준다") {
            val repository = mockk<PlannerRepository>()
            every { repository.deleteDraftsCreatedBefore(PlannerFixtures.createdAt) } returns 3

            PlannerWriter(repository).purgeDrafts(PlannerFixtures.createdAt) shouldBe 3
        }
    })
