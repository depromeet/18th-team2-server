package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.controller.dto.ParticipantResponse
import com.team2.server.party.controller.dto.PartyInfoResponse
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
) {
    fun getPartyInfo(shareLink: String, userId: Long?): PartyInfoResponse {
        val party = partyRepository.findByShareLink(shareLink)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        val myParticipant = if (userId != null) {
            val user = userRepository.findById(userId).orElse(null)
            user?.let { participantRepository.findByPartyAndUser(party, it) }
                ?.let { ParticipantResponse.from(it) }
        } else {
            null
        }

        return PartyInfoResponse.from(party, myParticipant)
    }
}
