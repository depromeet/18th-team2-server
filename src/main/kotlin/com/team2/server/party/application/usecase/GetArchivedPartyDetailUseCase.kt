package com.team2.server.party.application.usecase

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.api.dto.ArchiveChatMessageResponse
import com.team2.server.party.api.dto.ArchiveParticipantResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import com.team2.server.party.api.dto.ArchiveRole
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetArchivedPartyDetailUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val imageUrlReader: ImageUrlReader,
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
        val paperCount = rollingPaperRepository.countByParty(party)

        val myPaper = rollingPaperRepository.findByWriter(myParticipant)
        val myPaperWrapperImageUrl: String? =
            myPaper?.let {
                imageUrlReader
                    .findFirstImageUrlByTargetIds(
                        ImageTargetType.ROLLING_PAPER_WRAPPER,
                        listOf(it.wrapper.id),
                    )[it.wrapper.id]
            }

        val realtime = if (party.partyOption == PartyOption.REALTIME) buildRealtimeSections(party) else null

        return ArchivePartyDetailResponse(
            partyId = party.id,
            partyName = party.name.orEmpty(),
            partyOption = party.partyOption,
            role = role,
            partyStartedAt = party.startedAt,
            partyEndedAt = party.endedAt(),
            participantCount = realtime?.participants?.size?.toLong() ?: 0L,
            paperCount = paperCount,
            participants = realtime?.participants ?: emptyList(),
            chatMessages = realtime?.chatMessages ?: emptyList(),
            chatHasMore = realtime?.chatHasMore ?: false,
            myPaperWritten = myPaper != null,
            myPaperContent = myPaper?.content,
            myPaperWriterNickname = myPaper?.writerNickname,
            myPaperWrapperImageUrl = myPaperWrapperImageUrl,
        )
    }

    private fun buildRealtimeSections(party: Party): RealtimeSections {
        val profiles = realtimeParticipantProfileRepository.findAllByPartyIdOrderByIdAsc(party.id)
        val participants = profiles.map { ArchiveParticipantResponse(nickname = it.nickname) }

        val chatTotal = chatMessageRepository.countByPartyId(party.id)
        val recentChat =
            chatMessageRepository.findRecentByPartyId(party.id, PageRequest.of(0, CHAT_RECENT_LIMIT))
        val chatMessages =
            recentChat.map { msg ->
                ArchiveChatMessageResponse(
                    id = msg.id,
                    authorName = msg.profile.nickname,
                    content = msg.content,
                    sentAt = msg.createdAt,
                )
            }
        return RealtimeSections(
            participants = participants,
            chatMessages = chatMessages,
            chatHasMore = chatTotal > chatMessages.size,
        )
    }

    private data class RealtimeSections(
        val participants: List<ArchiveParticipantResponse>,
        val chatMessages: List<ArchiveChatMessageResponse>,
        val chatHasMore: Boolean,
    )

    companion object {
        const val CHAT_RECENT_LIMIT: Int = 50
    }
}
