package com.team2.server.burstgame.application.support

import com.team2.server.burstgame.domain.BurstGameParticipantInfo
import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
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
    ): BurstGameParticipantInfo {
        resolveLiveOpenRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, userId, participantToken)
        val characterId = profile.character?.id
        val imageUrl =
            characterId?.let {
                imageUrlReader.findFirstImageUrlByTargetIds(ImageTargetType.CHARACTER, listOf(it))[it]
            }
        return BurstGameParticipantInfo(
            participantId = profile.participant.id,
            nickname = profile.nickname,
            characterId = characterId,
            characterImageUrl = imageUrl,
            role =
                if (profile.participant.isCelebrant) {
                    ParticipantRole.CELEBRANT.name
                } else {
                    ParticipantRole.PARTICIPANT.name
                },
        )
    }
}
