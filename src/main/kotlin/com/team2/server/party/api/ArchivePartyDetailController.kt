package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ArchiveChatMessageItem
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.ArchiveChatMessageResponse
import com.team2.server.party.api.dto.ArchiveParticipantResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import com.team2.server.party.application.dto.ArchiveParticipantItem
import com.team2.server.party.application.dto.ArchivePartyDetailResult
import com.team2.server.party.application.usecase.GetArchivedPartyDetailUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/archive")
class ArchivePartyDetailController(
    private val getArchivedPartyDetailUseCase: GetArchivedPartyDetailUseCase,
) : ArchivePartyDetailApi {
    @GetMapping("/party/{partyId}")
    override fun getArchivedPartyDetail(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchivePartyDetailResponse> =
        ApiResponse.success(getArchivedPartyDetailUseCase.invoke(partyId, principal.userId).toResponse())

    private fun ArchivePartyDetailResult.toResponse(): ArchivePartyDetailResponse =
        ArchivePartyDetailResponse(
            partyId = partyId,
            celebrantNickname = celebrantNickname,
            partyOption = partyOption,
            role = role,
            partyStartedAt = partyStartedAt,
            partyEndedAt = partyEndedAt,
            participantCount = participantCount,
            paperCount = paperCount,
            participants = participants.map { it.toResponse() },
            chatMessages = chatMessages.map { it.toResponse() },
            chatHasMore = chatHasMore,
            myPaperWritten = myPaperWritten,
            myPaperContent = myPaperContent,
            myPaperWriterNickname = myPaperWriterNickname,
            myPaperToppingImageUrl = myPaperToppingImageUrl,
        )

    private fun ArchiveParticipantItem.toResponse(): ArchiveParticipantResponse =
        ArchiveParticipantResponse(nickname = nickname)

    private fun ArchiveChatMessageItem.toResponse(): ArchiveChatMessageResponse =
        ArchiveChatMessageResponse(
            id = id,
            authorName = authorName,
            content = content,
            sentAt = sentAt,
        )
}
