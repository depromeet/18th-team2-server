package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.PartyService
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreatePaperOnlyPartyUseCase(
    private val partyService: PartyService,
    private val partyInviteService: PartyInviteService,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun invoke(
        userId: Long,
        command: CreatePaperOnlyPartyCommand,
    ): Long {
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val partyId = partyService.createPaperOnlyParty(userId = userId, user = user, command = command)
        partyInviteService.activateInviteLink(partyId = partyId, userId = userId)
        return partyId
    }
}
