package com.team2.server.chat.usecase

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.UserLeftEventPayload
import com.team2.server.chat.service.ChatSseService
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.PartyRepository
import org.hibernate.Hibernate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class LeaveChatUseCase(
    private val partyRepository: PartyRepository,
    private val profileService: RealtimeParticipantProfileService,
    private val chatSseService: ChatSseService,
) {
    @Transactional(readOnly = true)
    fun leave(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ) {
        resolveRealtimeParty(partyId)
        val profile = profileService.resolveProfile(partyId, userId, participantToken)

        val payload =
            UserLeftEventPayload(
                nickname = profile.nickname,
                role = if (profile.participant.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
            )

        chatSseService.leave(profile.participantToken)
        chatSseService.broadcastAfterCommit(
            partyId,
            SseEmitter
                .event()
                .name("user-left")
                .data(payload)
                .build(),
        )
    }

    private fun resolveRealtimeParty(partyId: Long): RealtimeParty {
        val party =
            partyRepository.findPartyById(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        if (party.partyOption != PartyOption.REALTIME) throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        return Hibernate.unproxy(party) as RealtimeParty
    }
}
