package com.team2.server.party.application.usecase

import com.team2.server.common.image.entity.Image
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
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
    private val imageRepository: ImageRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): PartyParticipantsResult {
        val callerParticipantId = participantService.requireCallerParticipant(partyId, userId, participantToken).id
        val party = partyService.requireRealtimeParty(partyId)

        val profiles = participantService.findOrderedProfiles(partyId)
        val characterIds = profiles.mapNotNull { it.character?.id }.distinct()
        val images =
            if (characterIds.isEmpty()) {
                emptyList()
            } else {
                imageRepository.findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(
                    ImageTargetType.CHARACTER,
                    characterIds,
                )
            }
        val defaultImageByCharacterId = images.toImageUrlMap(DEFAULT_CHARACTER_IMAGE_SORT_ORDER)
        val ownerImageByCharacterId = images.toImageUrlMap(OWNER_CHARACTER_IMAGE_SORT_ORDER)

        val items =
            profiles.mapIndexed { index, profile ->
                val participant = profile.participant
                val isOwner = participant.user?.id == party.ownerId
                PartyParticipantResult(
                    participantId = participant.id,
                    joinOrder = index + 1,
                    nickname = profile.nickname,
                    characterId = profile.character?.id,
                    characterImageUrl =
                        resolveCharacterImageUrl(
                            characterId = profile.character?.id,
                            isOwner = isOwner,
                            ownerImages = ownerImageByCharacterId,
                            defaultImages = defaultImageByCharacterId,
                        ),
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

    private fun List<Image>.toImageUrlMap(sortOrder: Int): Map<Long, String> =
        filter { it.sortOrder == sortOrder }.associate { it.targetId to it.imageUrl }

    private fun resolveCharacterImageUrl(
        characterId: Long?,
        isOwner: Boolean,
        ownerImages: Map<Long, String>,
        defaultImages: Map<Long, String>,
    ): String? =
        characterId?.let { id ->
            if (isOwner) ownerImages[id] ?: defaultImages[id] else defaultImages[id]
        }

    private companion object {
        private const val DEFAULT_CHARACTER_IMAGE_SORT_ORDER = 0
        private const val OWNER_CHARACTER_IMAGE_SORT_ORDER = 2
    }
}
