package com.poppick.poppick.feature.planner.dataaccess

import com.poppick.poppick.feature.planner.PlannerFixtures
import com.poppick.poppick.feature.planner.dataaccess.entity.PlannerEntity
import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.domain.GeoPoint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PlannerEntityMappingTest :
    FunSpec({
        test("Planner → PlannerEntity → Planner 왕복이 같다(stops 포함)") {
            val planner = PlannerFixtures.planner()

            val entity = PlannerEntity.from(planner)

            entity.stops.map { it.planner } shouldBe List(3) { entity }
            entity.toDomain() shouldBe planner
        }

        test("stops 는 visitOrder 순으로 읽는다") {
            val planner = PlannerFixtures.planner()
            val entity = PlannerEntity.from(planner).apply { stops.reverse() }

            entity.toDomain().stops.map { it.visitOrder } shouldBe listOf(1, 2, 3)
        }

        test("next_path 는 [{lat, lng}] JSON 으로 직렬화되고, 정수로 읽힌 숫자도 복원된다") {
            val stop = PlannerEntity.from(PlannerFixtures.planner()).stops.first()

            Fixtures.jsonMapper.writeValueAsString(stop.nextPath) shouldBe """[{"lat":37.54,"lng":127.05},{"lat":37.55,"lng":127.06}]"""

            // jsonb 에서 정수로 읽힌 경우(127 → Integer)
            @Suppress("UNCHECKED_CAST")
            stop.nextPath = Fixtures.jsonMapper.readValue("""[{"lat":37,"lng":127}]""", List::class.java) as List<Map<String, Number>>
            stop.toDomain().nextPath shouldBe listOf(GeoPoint(37.0, 127.0))
        }
    })
