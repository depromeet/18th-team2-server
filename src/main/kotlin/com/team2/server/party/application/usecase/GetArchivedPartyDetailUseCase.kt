package com.team2.server.party.application.usecase

import com.team2.server.chat.usecase.GetArchiveChatSectionUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.ArchiveParticipantItem
import com.team2.server.party.application.dto.ArchivePartyDetailResult
import com.team2.server.party.application.dto.ArchiveRole
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.usecase.GetArchiveMyPaperSectionUseCase
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetArchivedPartyDetailUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val getArchiveChatSectionUseCase: GetArchiveChatSectionUseCase,
    private val getArchiveMyPaperSectionUseCase: GetArchiveMyPaperSectionUseCase,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long,
    ): ArchivePartyDetailResult {
        val party = partyRepository.findByIdOrNull(partyId) ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        val myParticipant =
            participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        val myPaper = getArchiveMyPaperSectionUseCase.invoke(party, myParticipant)
        val isRealtime = party.partyOption == PartyOption.REALTIME
        val participants = if (isRealtime) loadParticipants(party.id) else emptyList()
        val chat = if (isRealtime) getArchiveChatSectionUseCase.invoke(party.id) else null
        return ArchivePartyDetailResult(
            partyId = party.id,
            celebrantNickname = party.celebrantNickname,
            partyOption = party.partyOption,
            role = if (party.ownerId == userId) ArchiveRole.HOST else ArchiveRole.PARTICIPANT,
            partyStartedAt = party.startedAt,
            partyEndedAt = party.endedAt(),
            participantCount = participants.size.toLong(),
            paperCount = myPaper.paperCount,
            participants = participants,
            chatMessages = chat?.messages ?: emptyList(),
            chatHasMore = chat?.hasMore ?: false,
            myPaperWritten = myPaper.myPaperWritten,
            myPaperContent = myPaper.myPaperContent,
            myPaperWriterNickname = myPaper.myPaperWriterNickname,
            myPaperToppingImageUrl = myPaper.myPaperToppingImageUrl,
        )
    }

    private fun loadParticipants(partyId: Long): List<ArchiveParticipantItem> =
        realtimeParticipantProfileRepository
            .findAllByPartyIdOrderByIdAsc(partyId)
            .map { ArchiveParticipantItem(nickname = it.nickname) }
}
