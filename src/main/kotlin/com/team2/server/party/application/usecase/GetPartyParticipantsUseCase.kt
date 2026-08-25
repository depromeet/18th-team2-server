package com.team2.server.party.application.usecase

import com.team2.server.common.image.application.port.ImageUrlPort
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.party.application.dto.PartyParticipantResult
import com.team2.server.party.application.dto.PartyParticipantsResult
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPartyParticipantsUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val imageUrlPort: ImageUrlPort,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): PartyParticipantsResult {
        val callerParticipantId = participantService.requireCallerParticipant(partyId, userId, participantToken).id
        val party = partyService.requireRealtimeParty(partyId)

        // SSE/WebSocket 연결 상태(presence)는 신뢰할 수 없는 신호라 판단 기준으로 쓰지 않는다.
        // 명시적으로 퇴장(hasLeft)하지 않은 참여자는 실시간 연결 여부와 무관하게 목록에 포함한다.
        val profiles =
            participantService
                .findOrderedProfiles(partyId)
                .filter { !it.participant.hasLeft }
        val characterIds = profiles.mapNotNull { it.character?.id }.distinct()
        val defaultImageByCharacterId = findCharacterImageUrls(characterIds, DEFAULT_CHARACTER_IMAGE_SORT_ORDER)
        val thumbnailImageByCharacterId = findCharacterImageUrls(characterIds, THUMBNAIL_CHARACTER_IMAGE_SORT_ORDER)
        val ownerImageByCharacterId = findCharacterImageUrls(characterIds, OWNER_CHARACTER_IMAGE_SORT_ORDER)

        val items =
            profiles.mapIndexed { index, profile ->
                val participant = profile.participant
                val characterId = profile.character?.id
                val isOwner = participant.user?.id == party.ownerId
                val ownerImageUrl = if (isOwner) characterId?.let { ownerImageByCharacterId[it] } else null
                PartyParticipantResult(
                    participantId = participant.id,
                    joinOrder = index + 1,
                    nickname = profile.nickname,
                    characterId = characterId,
                    characterImageUrl = ownerImageUrl ?: characterId?.let { defaultImageByCharacterId[it] },
                    thumbnailImageUrl = characterId?.let { thumbnailImageByCharacterId[it] },
                    isOwner = isOwner,
                    isCelebrant = participant.isCelebrant,
                    isMe = participant.id == callerParticipantId,
                )
            }
        return PartyParticipantsResult(
            totalCount = items.size,
            maxCount = RealtimeParty.MAX_PARTICIPANTS,
            participants = items,
        )
    }

    private fun findCharacterImageUrls(
        characterIds: Collection<Long>,
        sortOrder: Int,
    ): Map<Long, String> =
        imageUrlPort.findImageUrlByTargetIdsAndSortOrder(ImageTargetType.CHARACTER, characterIds, sortOrder)

    private companion object {
        // CHARACTER 이미지 sort_order — V2 시드: 기본(Shape=Default)=0, 썸네일(Shape=Circle)=1 / V8 시드: 주최자 꼬깔모자=2
        private const val DEFAULT_CHARACTER_IMAGE_SORT_ORDER = 0
        private const val THUMBNAIL_CHARACTER_IMAGE_SORT_ORDER = 1
        private const val OWNER_CHARACTER_IMAGE_SORT_ORDER = 2
    }
}
