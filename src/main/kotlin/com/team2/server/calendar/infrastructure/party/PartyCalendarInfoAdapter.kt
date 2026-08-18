package com.team2.server.calendar.infrastructure.party

import com.team2.server.calendar.application.port.PartyCalendarInfo
import com.team2.server.calendar.application.port.PartyCalendarInfoPort
import com.team2.server.calendar.domain.vo.CelebrationKind
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.PartyService
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyPurpose
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class PartyCalendarInfoAdapter(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val partyInviteService: PartyInviteService,
    @Value("\${app.web-base-url}") private val webBaseUrl: String,
) : PartyCalendarInfoPort {
    override fun loadForMember(
        partyId: Long,
        userId: Long,
        now: LocalDateTime,
    ): PartyCalendarInfo {
        val party = partyService.requireParty(partyId)
        requireMember(party, partyId, userId)

        return PartyCalendarInfo(
            partyId = partyId,
            celebrationKind = party.purpose.toCelebrationKind(),
            celebrantName = party.celebrantNickname ?: party.name,
            startedAt = party.startedAt,
            inviteUrl = findInviteUrl(partyId, now),
        )
    }

    private fun requireMember(
        party: Party,
        partyId: Long,
        userId: Long,
    ) {
        if (party.ownerId == userId) return
        val participant = participantService.requireCallerParticipant(partyId, userId, null)
        if (participant.hasLeft) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }

    /**
     * 사용 가능한 초대 링크가 없어도 캘린더 등록 자체는 성공시킨다.
     * PartyInviteService 에는 nullable 조회가 없어 예외를 흡수한다.
     */
    private fun findInviteUrl(
        partyId: Long,
        now: LocalDateTime,
    ): String? =
        runCatching { partyInviteService.findLatestUsableInviteToken(partyId, now) }
            .getOrNull()
            ?.let { "$webBaseUrl/invite/$it" }

    private fun PartyPurpose.toCelebrationKind(): CelebrationKind =
        when (this) {
            PartyPurpose.BIRTHDAY -> CelebrationKind.BIRTHDAY
            PartyPurpose.JOB_CHANGE -> CelebrationKind.JOB_CHANGE
            PartyPurpose.WEDDING -> CelebrationKind.WEDDING
        }
}
