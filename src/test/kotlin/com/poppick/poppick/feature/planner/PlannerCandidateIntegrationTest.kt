package com.poppick.poppick.feature.planner

import com.poppick.poppick.config.properties.OpenAiProperties
import com.poppick.poppick.feature.planner.domain.CandidateQueryText
import com.poppick.poppick.feature.popup.dataaccess.client.openai.OpenAiEmbeddingClient
import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEmbeddingEntity
import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.PopupEmbedding
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupEmbeddingReader
import com.poppick.poppick.feature.popup.implement.PopupReader
import com.poppick.poppick.global.util.KST
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 후보 팝업 벡터 검색을 실 DB(pgvector) 로 확인한다(수동 검증용).
 * local 프로파일(DB_* · OPENAI_API_KEY) 이 필요하다. 픽스처 케이스는 @Transactional 로 롤백된다.
 * 픽스처는 전용 model 명으로 저장해 실데이터와 섞이지 않는다. area 는 favorite_area 3(홍대) · 4(잠실) 를 쓴다.
 */
@Disabled("수동 실행 전용: 실 DB 쓰기(롤백) · printHongdae 는 OpenAI 과금")
@SpringBootTest
@ActiveProfiles("local")
class PlannerCandidateIntegrationTest {
    @Autowired
    lateinit var popupRepository: PopupRepository

    @Autowired
    lateinit var popupEmbeddingRepository: PopupEmbeddingRepository

    @Autowired
    lateinit var popupEmbeddingReader: PopupEmbeddingReader

    @Autowired
    lateinit var popupReader: PopupReader

    @Autowired
    lateinit var openAiEmbeddingClient: OpenAiEmbeddingClient

    @Autowired
    lateinit var openAiProperties: OpenAiProperties

    private val model = "planner-it-model"
    private val today = LocalDate.now(KST)
    private val areaId = 3

    /** 쿼리와 같은 방향(e0) · 직교(e1) · 반대(-e0). */
    private val query = unit(0)
    private val same = unit(0)
    private val orthogonal = unit(1)
    private val opposite = unit(0, -1f)

    /** 필터마다 하나씩 어긋나게 만든 팝업은 전부 쿼리와 같은 방향이라, 걸러지지 않으면 맨 앞에 온다. */
    private class Seed(
        val same: Long,
        val orthogonal: Long,
        val opposite: Long,
        val otherArea: Long,
        val notStarted: Long,
        val ended: Long,
        val noCoordinates: Long,
        val excluded: Long,
    )

    private fun seed(): Seed {
        fun save(
            name: String,
            vector: FloatArray,
            area: Int = areaId,
            startDate: LocalDate? = today.minusDays(3),
            endDate: LocalDate = today.plusDays(10),
            latitude: Double? = 37.5563,
        ): Long {
            val popup =
                popupRepository.saveAndFlush(
                    PopupEntity(
                        source = SourceType.KAKAO_MAP,
                        externalId = "planner-it-$name",
                        title = "플래너 검색 테스트 $name",
                        areaId = area,
                        startDate = startDate,
                        endDate = endDate,
                        latitude = latitude,
                        longitude = 126.9236,
                    ),
                )
            popupEmbeddingRepository.saveAndFlush(
                PopupEmbeddingEntity(
                    popupId = popup.id!!,
                    kind = PopupEmbedding.KIND_PROFILE,
                    model = model,
                    contentText = name,
                    contentHash = name,
                    embedding = vector,
                ),
            )
            return popup.id!!
        }

        return Seed(
            same = save("same", same, startDate = null),
            orthogonal = save("orthogonal", orthogonal),
            opposite = save("opposite", opposite),
            otherArea = save("other-area", same, area = 4),
            notStarted = save("not-started", same, startDate = today.plusDays(1)),
            ended = save("ended", same, endDate = today.minusDays(1)),
            noCoordinates = save("no-coordinates", same, latitude = null),
            excluded = save("excluded", same),
        )
    }

    private fun search(
        seed: Seed,
        vector: FloatArray? = query,
        exclude: Set<Long> = setOf(seed.excluded),
        limit: Int = 10,
    ) = popupEmbeddingReader.searchSimilar(vector, model, areaId, today, exclude, limit)

    @Test
    @Transactional
    fun `코사인 거리 오름차순으로 돌려준다`() {
        val seed = seed()

        val hits = search(seed)

        hits.map { it.popupId } shouldBe listOf(seed.same, seed.orthogonal, seed.opposite)
        hits[0].distance!! shouldBe (0.0 plusOrMinus 1e-6)
        hits[1].distance!! shouldBe (1.0 plusOrMinus 1e-6)
        hits[2].distance!! shouldBe (2.0 plusOrMinus 1e-6)
    }

    @Test
    @Transactional
    fun `area 가 다르면 제외한다`() {
        val seed = seed()

        search(seed).map { it.popupId } shouldNotContain seed.otherArea
    }

    @Test
    @Transactional
    fun `방문일이 기간 밖이면 제외한다`() {
        val seed = seed()

        val ids = search(seed).map { it.popupId }

        ids shouldNotContain seed.notStarted
        ids shouldNotContain seed.ended
    }

    @Test
    @Transactional
    fun `좌표가 없으면 제외한다`() {
        val seed = seed()

        search(seed).map { it.popupId } shouldNotContain seed.noCoordinates
    }

    @Test
    @Transactional
    fun `excludePopupIds 를 제외한다`() {
        val seed = seed()

        search(seed).map { it.popupId } shouldNotContain seed.excluded
    }

    @Test
    @Transactional
    fun `excludePopupIds 가 비면 제외 조건을 생략한다`() {
        val seed = seed()

        search(seed, exclude = emptySet()).map { it.popupId } shouldBe listOf(seed.excluded, seed.same, seed.orthogonal, seed.opposite)
    }

    @Test
    @Transactional
    fun `limit 을 적용한다`() {
        val seed = seed()

        search(seed, limit = 2).map { it.popupId } shouldBe listOf(seed.same, seed.orthogonal)
    }

    @Test
    @Transactional
    fun `queryVector 가 null 이면 popup_id 내림차순이고 distance 는 null`() {
        val seed = seed()

        val hits = search(seed, vector = null)

        hits.map { it.popupId } shouldBe listOf(seed.opposite, seed.orthogonal, seed.same)
        hits.forEach { it.distance.shouldBeNull() }
    }

    /** 실데이터 · 실제 OpenAI 임베딩으로 홍대 후보를 출력한다(과금 소액). 결과는 눈으로 확인. */
    @Test
    fun printHongdae() {
        val text = CandidateQueryText.build(listOf("캐릭터/IP"), listOf("사진 찍기"), null)!!
        val vector = openAiEmbeddingClient.embed(listOf(text)).vectors.single()

        val hits = popupEmbeddingReader.searchSimilar(vector, openAiProperties.embeddingModel, 3, today, emptySet(), 40)
        val titles = popupReader.findAllByIds(hits.map { it.popupId }).associate { it.id to it.title }

        println("query=${text.replace("\n", " / ")} hits=${hits.size}")
        hits.forEach { println("${it.popupId}\t${"%.4f".format(it.distance)}\t${titles[it.popupId]}") }
    }

    private fun unit(
        index: Int,
        value: Float = 1f,
    ) = FloatArray(DIMENSIONS).also { it[index] = value }

    companion object {
        /** popup_embedding.embedding 컬럼 차원. */
        private const val DIMENSIONS = 1536
    }
}
