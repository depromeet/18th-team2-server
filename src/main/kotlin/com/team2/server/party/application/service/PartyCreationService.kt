package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.User
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PartyCreationService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val characterRepository: CharacterRepository,
) {
    fun createRealtimeParty(
        userId: Long,
        user: User,
        command: CreateRealtimePartyCommand,
    ): Long {
        requireOwnerUserMatches(userId, user)
        val party =
            RealtimeParty(
                ownerId = userId,
                celebrantNickname = command.celebrantNickname,
                startedAt = LocalDateTime.of(command.startedDate, command.startTime),
            )
        val saved = partyRepository.save(party)
        val participant =
            participantRepository.save(
                Participant(
                    party = saved,
                    user = user,
                    isCelebrant = true,
                    // 파티 생성 시점엔 아직 실제 입장(/ws)이 아니다 — 이후 최초 입장 시 Participant.enter() 로 뒤집는다.
                    hasEntered = false,
                ),
            )
        val character =
            characterRepository
                .findById(command.characterId)
                .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(
                participant = participant,
                nickname = command.celebrantNickname,
                character = character,
            ),
        )
        return saved.id
    }

    fun createPaperOnlyParty(
        userId: Long,
        user: User,
        command: CreatePaperOnlyPartyCommand,
    ): Long {
        requireOwnerUserMatches(userId, user)
        val party =
            PaperOnlyParty(
                ownerId = userId,
                celebrantNickname = command.celebrantNickname,
                startedAt = command.startedDate.atStartOfDay(),
            )
        val saved = partyRepository.save(party)
        participantRepository.save(
            Participant(
                party = saved,
                user = user,
                isCelebrant = true,
            ),
        )
        return saved.id
    }

    private fun requireOwnerUserMatches(
        userId: Long,
        user: User,
    ) {
        if (userId != user.id) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }
}
