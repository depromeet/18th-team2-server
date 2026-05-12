package com.team2.server.party.infrastructure

import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.party.domain.entity.Character
import org.springframework.stereotype.Component

@Component
class CharacterImageResolver(
    private val imageRepository: ImageRepository,
) {
    fun resolve(character: Character): String? =
        imageRepository
            .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(ImageTargetType.CHARACTER, character.id)
            ?.imageUrl
}
