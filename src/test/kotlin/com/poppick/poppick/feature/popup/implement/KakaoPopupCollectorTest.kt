package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.Fixtures
import com.poppick.poppick.feature.popup.dataaccess.client.kakao.KakaoMapClient
import com.poppick.poppick.feature.popup.dataaccess.entity.SearchKeywordEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.SearchKeywordRepository
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.feature.popup.implement.PopupWriter.UpsertResult
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import java.util.Collections
import java.util.concurrent.Executors

class KakaoPopupCollectorTest :
    FunSpec({
        val executor = Executors.newFixedThreadPool(4)
        afterSpec { executor.shutdownNow() }

        fun place(
            id: String,
            jibun: String?,
            road: String? = null,
        ) = KakaoPlace(id = id, placeName = "팝업$id", addressJibun = jibun, addressRoad = road)

        val seongsu = place("1", "서울 성동구 성수동2가 1")
        val hanam = place("2", "경기 하남시 신장동 1", "경기 하남시 미사대로 1")
        val roadOnly = place("3", null, "서울 성동구 아차산로 1")
        val seongsuAgain = seongsu.copy(placeName = "중복")
        val gangnam = place("4", "서울 강남구 역삼동 1")

        test("서울 필터 · place id dedupe 후 호출 스레드에서 순차 upsert, 검색어 하나가 실패해도 나머지는 저장한다") {
            val searchKeywordRepository = mockk<SearchKeywordRepository>()
            val kakaoMapClient = mockk<KakaoMapClient>()
            val popupWriter = mockk<PopupWriter>()
            val collector =
                KakaoPopupCollector(searchKeywordRepository, kakaoMapClient, popupWriter, Fixtures.collectionProperties(), executor)

            every { searchKeywordRepository.findAllByTargetSourceAndIsActiveTrueOrderByIdAsc(SourceType.KAKAO_MAP) } returns
                listOf("성수동 팝업스토어", "실패하는 검색어", "강남 팝업스토어").mapIndexed { i, keyword ->
                    SearchKeywordEntity(keyword = keyword, targetSource = SourceType.KAKAO_MAP, id = i + 1L)
                }
            every { kakaoMapClient.searchAll("성수동 팝업스토어") } returns listOf(seongsu, hanam, roadOnly)
            every { kakaoMapClient.searchAll("실패하는 검색어") } throws IllegalStateException("kakao 500")
            every { kakaoMapClient.searchAll("강남 팝업스토어") } returns listOf(seongsuAgain, gangnam)

            val upsertThreads = Collections.synchronizedList(mutableListOf<String>())
            every { popupWriter.upsertFromKakao(any()) } answers {
                upsertThreads += Thread.currentThread().name
                if (firstArg<KakaoPlace>().id == "4") UpsertResult.UPDATED else UpsertResult.CREATED
            }

            val report = collector.collect()

            verifySequence {
                popupWriter.upsertFromKakao(seongsu)
                popupWriter.upsertFromKakao(roadOnly)
                popupWriter.upsertFromKakao(gangnam)
            }
            upsertThreads shouldContainOnly listOf(Thread.currentThread().name)

            report.keywords shouldBe 3
            report.failedKeywords shouldBe 1
            report.fetched shouldBe 5
            report.seoul shouldBe 4
            report.unique shouldBe 3
            report.created shouldBe 2
            report.updated shouldBe 1
            report.failed shouldBe 0
            report.timedOut shouldBe false
        }

        test("저장 중 예외가 난 장소는 failed 로 세고 나머지는 계속 저장한다") {
            val searchKeywordRepository = mockk<SearchKeywordRepository>()
            val kakaoMapClient = mockk<KakaoMapClient>()
            val popupWriter = mockk<PopupWriter>()
            val collector =
                KakaoPopupCollector(searchKeywordRepository, kakaoMapClient, popupWriter, Fixtures.collectionProperties(), executor)

            every { searchKeywordRepository.findAllByTargetSourceAndIsActiveTrueOrderByIdAsc(SourceType.KAKAO_MAP) } returns
                listOf(SearchKeywordEntity(keyword = "성수동 팝업스토어", targetSource = SourceType.KAKAO_MAP, id = 1))
            every { kakaoMapClient.searchAll(any()) } returns listOf(seongsu, gangnam)
            every { popupWriter.upsertFromKakao(seongsu) } throws RuntimeException("unique violation")
            every { popupWriter.upsertFromKakao(gangnam) } returns UpsertResult.UPDATED

            val report = collector.collect()

            report.failed shouldBe 1
            report.updated shouldBe 1
        }
    })
