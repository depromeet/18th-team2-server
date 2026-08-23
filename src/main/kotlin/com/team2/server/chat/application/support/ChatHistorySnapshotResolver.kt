package com.team2.server.chat.application.support

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import org.springframework.stereotype.Component

@Component
class ChatHistorySnapshotResolver(
    private val chatMessageRepository: ChatMessageRepository,
    private val imageUrlReader: ImageUrlReader,
) {
    data class Snapshot(
        val messages: List<ChatMessageResponse>,
        val enteringCharacterImageUrl: String?,
    )

    fun resolve(
        partyId: Long,
        enteringCharacterId: Long?,
    ): Snapshot {
        val rawMessages = chatMessageRepository.findAllByPartyIdWithProfileOrderByCreatedAtAsc(partyId)
        val characterIds =
            (rawMessages.mapNotNull { it.profile.character?.id } + enteringCharacterId)
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
        return Snapshot(
            messages = messages,
            enteringCharacterImageUrl = enteringCharacterId?.let { imageUrlMap[it] },
        )
    }

    private companion object {
        const val CHARACTER_THUMBNAIL_SORT_ORDER = 1
    }
}
