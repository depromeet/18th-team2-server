package com.team2.server.party.application.usecase

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
        val imageUrlByCharacterId =
            if (characterIds.isEmpty()) {
                emptyMap()
            } else {
                imageRepository
                    .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(ImageTargetType.CHARACTER, characterIds)
                    .filter { it.sortOrder == CHARACTER_IMAGE_SORT_ORDER }
                    .associate { it.targetId to it.imageUrl }
            }

        val items =
            profiles.mapIndexed { index, profile ->
                val participant = profile.participant
                PartyParticipantResult(
                    participantId = participant.id,
                    joinOrder = index + 1,
                    nickname = profile.nickname,
                    characterId = profile.character?.id,
                    characterImageUrl = profile.character?.id?.let { imageUrlByCharacterId[it] },
                    isOwner = participant.user?.id == party.ownerId,
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

    private companion object {
        private const val CHARACTER_IMAGE_SORT_ORDER = 0
    }
}
