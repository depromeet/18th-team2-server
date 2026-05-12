package com.team2.server.party.usecase

import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.party.application.dto.CharacterResult
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetCharactersUseCase(
    private val characterRepository: CharacterRepository,
    private val imageRepository: ImageRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(): List<CharacterResult> {
        val characters = characterRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
        if (characters.isEmpty()) {
            return emptyList()
        }

        val imagesByCharacterId =
            imageRepository
                .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(
                    ImageTargetType.CHARACTER,
                    characters.map { it.id },
                ).groupBy { it.targetId }

        return characters.map { character ->
            val imagesBySortOrder = imagesByCharacterId[character.id].orEmpty().associateBy { it.sortOrder }
            CharacterResult(
                characterId = character.id,
                name = character.name,
                characterImageUrl = imagesBySortOrder[CHARACTER_IMAGE_SORT_ORDER]?.imageUrl,
                characterThumbnailImageUrl = imagesBySortOrder[CHARACTER_THUMBNAIL_IMAGE_SORT_ORDER]?.imageUrl,
            )
        }
    }

    private companion object {
        private const val CHARACTER_IMAGE_SORT_ORDER = 0
        private const val CHARACTER_THUMBNAIL_IMAGE_SORT_ORDER = 1
    }
}
