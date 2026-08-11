package com.team2.server.chat.usecase

import com.team2.server.chat.domain.vo.ParticipantRole
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.UserEnteredEventPayload
import com.team2.server.chat.infrastructure.sse.ChatSseGateway
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.dto.RealtimePartyStateResult
import com.team2.server.party.domain.entity.RealtimeParty
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class EnterAndSubscribeChatUseCase(
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase,
    private val chatMessageRepository: ChatMessageRepository,
    private val imageUrlReader: ImageUrlReader,
    private val chatSseGateway: ChatSseGateway,
) {
    @Transactional
    fun enterAndSubscribe(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): SseEmitter {
        val enterResult = enterRealtimePartyUseCase.enter(inviteToken, userId, request)

        val rawMessages =
            chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(enterResult.partyId)
        val characterIds =
            (rawMessages.mapNotNull { it.profile.character?.id } + enterResult.characterId)
                .filterNotNull()
                .distinct()
        val imageUrlMap =
            imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                ImageTargetType.CHARACTER,
                characterIds,
                CHARACTER_THUMBNAIL_SORT_ORDER,
            )
        val messages =
            rawMessages.map {
                ChatMessageResponse.from(
                    message = it,
                    isCelebrant = it.profile.participant.isCelebrant,
                    imageUrl = it.profile.character?.let { c -> imageUrlMap[c.id] },
                )
            }
        val enteredPayload =
            UserEnteredEventPayload(
                nickname = enterResult.nickname,
                characterId = enterResult.characterId,
                characterImageUrl = enterResult.characterId?.let { imageUrlMap[it] },
                role = if (enterResult.isCelebrant) ParticipantRole.CELEBRANT else ParticipantRole.PARTICIPANT,
            )

        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)
        chatSseGateway.subscribe(enterResult.partyId, emitter, enterResult.participantToken)
        sendPartyState(emitter, enterResult.partyState)
        sendEntered(emitter, enterResult.participantToken, messages)

        chatSseGateway.broadcastAfterCommit(
            enterResult.partyId,
            SseEmitter
                .event()
                .name("user-entered")
                .data(enteredPayload)
                .build(),
            excludeToken = enterResult.participantToken,
        )
        return emitter
    }

    private fun sendPartyState(
        emitter: SseEmitter,
        partyState: RealtimePartyStateResult,
    ) {
        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("party-state")
                    .data(partyState)
                    .build(),
            )
        } catch (e: IllegalStateException) {
            emitter.completeWithError(e)
        } catch (e: java.io.IOException) {
            emitter.completeWithError(e)
        }
    }

    private fun sendEntered(
        emitter: SseEmitter,
        participantToken: String,
        messages: List<ChatMessageResponse>,
    ) {
        try {
            emitter.send(
                SseEmitter
                    .event()
                    .name("entered")
                    .data(EnterRealtimePartyResponse(participantToken, messages))
                    .build(),
            )
        } catch (e: IllegalStateException) {
            emitter.completeWithError(e)
        } catch (e: java.io.IOException) {
            emitter.completeWithError(e)
        }
    }

    companion object {
        private const val CHARACTER_THUMBNAIL_SORT_ORDER = 1
        private const val SSE_GRACE_CLEANUP_SECONDS = 2L
        private const val EMITTER_TIMEOUT_MS =
            (
                (
                    RealtimeParty.ENTERABLE_BEFORE_MINUTES +
                        RealtimeParty.START_GRACE_MINUTES +
                        RealtimeParty.LIVE_DURATION_MINUTES
                ) * 60 +
                    RealtimeParty.LIVE_END_COUNTDOWN_SECONDS +
                    SSE_GRACE_CLEANUP_SECONDS
            ) * 1000L
    }
}
