package com.team2.server.chat.application.support

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.UserLeftEventPayload
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.usecase.StartRealtimePartyEndUseCase
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Component

/**
 * 퇴장 처리(참가자 상태 변경 + 주최자 퇴장 시 파티 종료 시작)와 퇴장 이벤트 페이로드 구성을 담당한다.
 *
 * SSE(REST)와 WebSocket 두 채널이 동일한 퇴장 처리를 써야 하므로
 * [ChatHistorySnapshotResolver] 와 같은 방식으로 UseCase 에서 분리했다.
 * 브로드캐스트와 SSE emitter 해제는 채널별 관심사라 각 UseCase 에 남는다.
 */
@Component
class ChatLeaveExecutor(
    private val participantService: ParticipantService,
    private val startRealtimePartyEndUseCase: StartRealtimePartyEndUseCase,
) {
    fun execute(
        party: RealtimeParty,
        profile: RealtimeParticipantProfile,
        userId: Long?,
    ): UserLeftEventPayload {
        participantService.leave(profile.participant)
        endPartyIfHostLeft(party, profile, userId)

        return UserLeftEventPayload(
            nickname = profile.nickname,
            role = if (profile.participant.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
        )
    }

    private fun endPartyIfHostLeft(
        party: RealtimeParty,
        profile: RealtimeParticipantProfile,
        userId: Long?,
    ) {
        if (userId == party.ownerId || profile.participant.user?.id == party.ownerId) {
            startRealtimePartyEndUseCase(party.id, party.ownerId)
        }
    }
}
