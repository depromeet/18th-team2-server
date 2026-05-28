package com.team2.server.fireworks.application.usecase

import com.team2.server.chat.application.port.PartySseEventPublisher
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Clock
import java.time.LocalDateTime

@Service
class TriggerFireworksUseCase(
    private val resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase,
    private val resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase,
    private val imageUrlReader: ImageUrlReader,
    private val partySseEventPublisher: PartySseEventPublisher,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ) {
        resolveLiveOpenRealtimePartyUseCase.invoke(partyId)
        val profile = resolveRealtimeParticipantProfileUseCase.invoke(partyId, userId, participantToken)

        val characterImageUrl =
            profile.character?.id?.let {
                imageUrlReader.findFirstImageUrlByTargetIds(ImageTargetType.CHARACTER, listOf(it))[it]
            }

        val payload =
            FireworksPayload(
                partyId = partyId,
                participantId = profile.participant.id,
                nickname = profile.nickname,
                characterId = profile.character?.id,
                characterImageUrl = characterImageUrl,
                role = if (profile.participant.isCelebrant) "CELEBRANT" else "PARTICIPANT",
                serverTime = LocalDateTime.now(clock),
            )

        partySseEventPublisher.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name("fireworks")
                .data(payload)
                .build(),
        )
    }

    data class FireworksPayload(
        val partyId: Long,
        val participantId: Long,
        val nickname: String,
        val characterId: Long?,
        val characterImageUrl: String?,
        val role: String,
        val serverTime: LocalDateTime,
    )
}
