package com.team2.server.party.service

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CreatePartyRequest
import com.team2.server.party.dto.CreatePartyResponse
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun createParty(
        userId: Long,
        request: CreatePartyRequest,
        partyOption: PartyOption,
    ): CreatePartyResponse {
        val user = findUser(userId)
        val party =
            when (partyOption) {
                PartyOption.REALTIME ->
                    RealtimeParty(
                        ownerId = userId,
                        celebrantNickname = request.celebrantNickname,
                        startedAt =
                            LocalDateTime.of(
                                request.startedDate,
                                requireNotNull(request.startTime) { "REALTIME 파티에는 startTime이 필요합니다." },
                            ),
                    )
                PartyOption.PAPER_ONLY ->
                    PaperOnlyParty(
                        ownerId = userId,
                        celebrantNickname = request.celebrantNickname,
                        startedAt = request.startedDate.atStartOfDay(),
                    )
            }
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

    @Transactional
    fun deleteParty(
        partyId: Long,
        userId: Long,
    ) {
        val party =
            partyRepository
                .findPartyById(partyId)
                .orElseThrow { BusinessException(ErrorCode.PARTY_NOT_FOUND) }

        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

        if (!LocalDateTime.now().isBefore(party.startedAt)) {
            throw BusinessException(ErrorCode.PARTY_ALREADY_STARTED)
        }

        val participants = participantRepository.findAllByPartyId(partyId)

        chatMessageRepository.deleteAllByPartyId(partyId)
        rollingPaperRepository.deleteAllByPartyId(partyId)
        if (party is RealtimeParty) {
            val participantIds = participants.map { it.id }
            realtimeParticipantProfileRepository.deleteAllByParticipantIdIn(participantIds)
        }
        participantRepository.deleteAll(participants)
        partyInviteRepository.deleteAllByPartyId(partyId)
        partyRepository.delete(party)
    }

    private fun findUser(userId: Long) =
        userRepository
            .findById(userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
}
