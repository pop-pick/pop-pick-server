package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupEmbeddingRepository
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PopupWriter(
    private val popupRepository: PopupRepository,
    private val popupEmbeddingRepository: PopupEmbeddingRepository,
) {
    companion object {
        /** IN 절 1회에 넣는 id 수. */
        private const val DELETE_CHUNK_SIZE = 1000
    }

    enum class UpsertResult { CREATED, UPDATED }

    data class DeleteResult(
        val popups: Int,
        val embeddings: Int,
    )

    /**
     * 카카오 장소 1건 upsert(장소 1건 = 트랜잭션 1개).
     * 기존 레코드는 장소 필드 · raw_payload · source_urls 만 갱신하고, 보강 결과(제목 · 기간 · 카테고리 등)는 건드리지 않는다.
     */
    @Transactional
    fun upsertFromKakao(place: KakaoPlace): UpsertResult {
        val entity =
            popupRepository.findBySourceAndExternalId(SourceType.KAKAO_MAP, place.id)
                ?: run {
                    popupRepository.save(PopupEntity.from(newPopup(place)))
                    return UpsertResult.CREATED
                }

        entity.placeName = place.placeName
        entity.addressRoad = place.addressRoad
        entity.addressJibun = place.addressJibun
        entity.latitude = place.latitude
        entity.longitude = place.longitude
        entity.rawPayload = place.raw
        entity.sourceUrls = (entity.sourceUrls.orEmpty() + listOfNotNull(place.placeUrl)).distinct()
        return UpsertResult.UPDATED
    }

    @Transactional
    fun save(popup: Popup): Popup = popupRepository.save(PopupEntity.from(popup)).toDomain()

    /**
     * 팝업과 그 임베딩을 삭제한다(호출 1회 = 트랜잭션 1개). FK 동작(CASCADE 여부)에 기대지 않고 임베딩 → 팝업 순으로 지운다.
     * IN 절은 DELETE_CHUNK_SIZE 단위로 나눈다.
     */
    @Transactional
    fun deleteAll(ids: List<Long>): DeleteResult {
        var popups = 0
        var embeddings = 0
        ids.chunked(DELETE_CHUNK_SIZE).forEach { chunk ->
            embeddings += popupEmbeddingRepository.deleteByPopupIdIn(chunk)
            popups += popupRepository.deleteByIds(chunk)
        }
        return DeleteResult(popups, embeddings)
    }

    private fun newPopup(place: KakaoPlace) =
        Popup(
            source = SourceType.KAKAO_MAP,
            externalId = place.id,
            placeId = place.id,
            placeResolution = PlaceResolution.EXACT,
            title = place.placeName,
            placeName = place.placeName,
            addressRoad = place.addressRoad,
            addressJibun = place.addressJibun,
            latitude = place.latitude,
            longitude = place.longitude,
            sourceUrls = listOfNotNull(place.placeUrl),
            rawPayload = place.raw,
        )
}
