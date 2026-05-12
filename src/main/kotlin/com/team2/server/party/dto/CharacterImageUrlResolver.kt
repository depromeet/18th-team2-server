package com.team2.server.party.dto

import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.party.domain.entity.Character
import org.springframework.stereotype.Component

@Component
class CharacterImageUrlResolver(
    private val imageRepository: ImageRepository,
) {
    fun resolve(character: Character): String? =
        imageRepository
            .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(ImageTargetType.CHARACTER, character.id)
            ?.imageUrl
}
