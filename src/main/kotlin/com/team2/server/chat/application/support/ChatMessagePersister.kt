package com.team2.server.chat.application.support

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.domain.entity.Party
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import org.springframework.stereotype.Component

/**
 * 채팅 메시지 저장 + 발신자 캐릭터 썸네일 조회 + 응답 변환을 담당한다.
 *
 * SSE(REST)와 WebSocket 두 채널이 동일한 저장/변환 로직을 써야 하므로
 * [ChatHistorySnapshotResolver] 와 같은 방식으로 UseCase 에서 분리했다.
 */
@Component
class ChatMessagePersister(
    private val chatMessageRepository: ChatMessageRepository,
    private val imageUrlReader: ImageUrlReader,
) {
    fun persist(
        party: Party,
        profile: RealtimeParticipantProfile,
        content: String,
    ): ChatMessageResponse {
        val message =
            chatMessageRepository.save(
                ChatMessage(content = content, party = party, profile = profile),
            )

        val imageUrl =
            message.profile.character?.id?.let {
                imageUrlReader.findImageUrlByTargetIdsAndSortOrder(
                    ImageTargetType.CHARACTER,
                    listOf(it),
                    CHARACTER_THUMBNAIL_SORT_ORDER,
                )[it]
            }
        return ChatMessageResponse.from(message, message.profile.participant.isCelebrant, imageUrl)
    }

    private companion object {
        private const val CHARACTER_THUMBNAIL_SORT_ORDER = 1
    }
}
