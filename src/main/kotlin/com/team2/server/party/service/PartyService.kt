package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.LocalTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createParty(
        userId: Long,
        request: CreatePartyRequest,
        partyOption: PartyOption,
    ): CreatePartyResponse {
        val user = findUser(userId)
        val startedAt =
            when (partyOption) {
                PartyOption.PAPER_ONLY ->
                    LocalDateTime.of(request.startedDate, request.startTime ?: LocalTime.MIDNIGHT)
                PartyOption.REALTIME ->
                    LocalDateTime.of(
                        request.startedDate,
                        requireNotNull(request.startTime) { "REALTIME 파티에는 startTime이 필요합니다." },
                    )
            }
        val party =
            Party(
                ownerId = userId,
                celebrantNickname = request.celebrantNickname,
                startedAt = startedAt,
                option = partyOption,
            )
        val saved = partyRepository.save(party)
        val participant =
            participantRepository.save(
                Participant(
                    party = saved,
                    user = user,
                    isCelebrant = true,
                ),
            )
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(
                participant = participant,
                nickname = request.celebrantNickname,
            ),
        )
        return CreatePartyResponse(partyId = saved.id)
    }

    private fun findUser(userId: Long) =
        userRepository
            .findById(userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
}
