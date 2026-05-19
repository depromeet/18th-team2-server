package com.team2.server.party.application.usecase

import com.team2.server.chat.dto.ArchiveChatMessageItem
import com.team2.server.chat.usecase.GetArchiveChatSectionUseCase
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.api.dto.ArchiveChatMessageResponse
import com.team2.server.party.api.dto.ArchiveParticipantResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import com.team2.server.party.api.dto.ArchiveRole
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
    ): ArchivePartyDetailResponse {
        val party =
            partyRepository.findByIdOrNull(partyId)
                ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        val myParticipant =
            participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

        val role = if (party.ownerId == userId) ArchiveRole.HOST else ArchiveRole.PARTICIPANT
        val myPaper = getArchiveMyPaperSectionUseCase.invoke(party, myParticipant)
        val isRealtime = party.partyOption == PartyOption.REALTIME
        val participants = if (isRealtime) buildParticipants(party.id) else emptyList()
        val chat = if (isRealtime) getArchiveChatSectionUseCase.invoke(party.id) else null

        return ArchivePartyDetailResponse(
            partyId = party.id,
            partyName = party.name.orEmpty(),
            partyOption = party.partyOption,
            role = role,
            partyStartedAt = party.startedAt,
            partyEndedAt = party.endedAt(),
            participantCount = participants.size.toLong(),
            paperCount = myPaper.paperCount,
            participants = participants,
            chatMessages = chat?.messages?.map(::toChatMessageResponse) ?: emptyList(),
            chatHasMore = chat?.hasMore ?: false,
            myPaperWritten = myPaper.myPaperWritten,
            myPaperContent = myPaper.myPaperContent,
            myPaperWriterNickname = myPaper.myPaperWriterNickname,
            myPaperWrapperImageUrl = myPaper.myPaperWrapperImageUrl,
        )
    }

    private fun buildParticipants(partyId: Long): List<ArchiveParticipantResponse> =
        realtimeParticipantProfileRepository
            .findAllByPartyIdOrderByIdAsc(partyId)
            .map { ArchiveParticipantResponse(nickname = it.nickname) }

    private fun toChatMessageResponse(item: ArchiveChatMessageItem): ArchiveChatMessageResponse =
        ArchiveChatMessageResponse(
            id = item.id,
            authorName = item.authorName,
            content = item.content,
            sentAt = item.sentAt,
        )
}
