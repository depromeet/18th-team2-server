package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Component

@Component
class BurstGameParticipantResolver(
    private val resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val imageUrlReader: ImageUrlReader,
) {
    fun resolve(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): BurstGameParticipantInfo = resolveWithParty(partyId, userId, participantToken).participant

    fun resolveWithParty(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): ResolvedRealtimeParticipant {
        val party = resolveLiveOpenRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, userId, participantToken)
        val characterId = profile.character?.id
        val thumbnailImageUrl =
            characterId?.let {
                imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                    ImageTargetType.CHARACTER,
                    listOf(it),
                    CHARACTER_THUMBNAIL_SORT_ORDER,
                )[it]
            }
        return ResolvedRealtimeParticipant(
            party = party,
            participant =
                BurstGameParticipantInfo(
                    participantId = profile.participant.id,
                    nickname = profile.nickname,
                    characterId = characterId,
                    characterThumbnailImageUrl = thumbnailImageUrl,
                    role =
                        if (profile.participant.isCelebrant) {
                            ParticipantRole.CELEBRANT.name
                        } else {
                            ParticipantRole.PARTICIPANT.name
                        },
                ),
        )
    }

    private companion object {
        const val CHARACTER_THUMBNAIL_SORT_ORDER = 1
    }

    data class ResolvedRealtimeParticipant(
        val party: RealtimeParty,
        val participant: BurstGameParticipantInfo,
    )
}
