package com.poppick.poppick.feature.popup.implement

import com.poppick.poppick.feature.popup.dataaccess.entity.PopupEntity
import com.poppick.poppick.feature.popup.dataaccess.repository.PopupRepository
import com.poppick.poppick.feature.popup.domain.KakaoPlace
import com.poppick.poppick.feature.popup.domain.PlaceResolution
import com.poppick.poppick.feature.popup.domain.Popup
import com.poppick.poppick.feature.popup.domain.SourceType
import com.poppick.poppick.global.exception.AppException
import com.poppick.poppick.global.exception.ErrorType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class PopupWriter(
    private val popupRepository: PopupRepository,
) {
    enum class UpsertResult { CREATED, UPDATED }

    /**
     * 카카오 장소 1건 upsert(장소 1건 = 트랜잭션 1개).
     * 기존 레코드는 장소 필드 · raw_payload · source_urls · last_seen_at 만 갱신하고, 보강 결과(제목 · 기간 · 카테고리 등)는 건드리지 않는다.
     * last_seen_at 은 값이 같아도 매번 바뀌므로, 카카오에서 사라진 장소와 유지 중인 장소를 구분할 수 있다.
     */
    @Transactional
    fun upsertFromKakao(
        place: KakaoPlace,
        seenAt: OffsetDateTime,
    ): UpsertResult {
        val entity =
            popupRepository.findBySourceAndExternalId(SourceType.KAKAO_MAP, place.id)
                ?: run {
                    popupRepository.save(PopupEntity.from(newPopup(place, seenAt)))
                    return UpsertResult.CREATED
                }

        entity.placeName = place.placeName
        entity.addressRoad = place.addressRoad
        entity.addressJibun = place.addressJibun
        entity.latitude = place.latitude
        entity.longitude = place.longitude
        entity.rawPayload = place.raw
        entity.sourceUrls = (entity.sourceUrls.orEmpty() + listOfNotNull(place.placeUrl)).distinct()
        entity.lastSeenAt = seenAt
        return UpsertResult.UPDATED
    }

    @Transactional
    fun save(popup: Popup): Popup = popupRepository.save(PopupEntity.from(popup)).toDomain()

    /**
     * 이미지 수집 결과 저장. image_urls · image_checked_at 두 필드만 바꾼다(엔티티 통째 저장 금지).
     * 못 찾았으면(imageUrls 가 비면) image_urls 는 그대로 두고 image_checked_at 만 갱신한다.
     */
    @Transactional
    fun updateImages(
        popupId: Long,
        imageUrls: List<String>,
        checkedAt: OffsetDateTime,
    ) {
        val entity = popupRepository.findByIdOrNull(popupId) ?: throw AppException(ErrorType.NOT_FOUND_DATA)
        if (imageUrls.isNotEmpty()) entity.imageUrls = imageUrls
        entity.imageCheckedAt = checkedAt
    }

    private fun newPopup(
        place: KakaoPlace,
        seenAt: OffsetDateTime,
    ) = Popup(
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
        lastSeenAt = seenAt,
    )
}
