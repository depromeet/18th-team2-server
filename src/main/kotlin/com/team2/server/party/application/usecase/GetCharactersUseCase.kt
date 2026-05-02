package com.team2.server.party.application.usecase

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.application.dto.CharacterResult
import com.team2.server.party.repository.CharacterRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetCharactersUseCase(
    private val characterRepository: CharacterRepository,
    private val imageRepository: ImageRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(): List<CharacterResult> =
        characterRepository
            .findAll(Sort.by(Sort.Direction.ASC, "id"))
            .map { character ->
                val url =
                    imageRepository
                        .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                            ImageTargetType.CHARACTER,
                            character.id,
                        )?.imageUrl
                CharacterResult(
                    characterId = character.id,
                    name = character.name,
                    characterImageUrl = url,
                )
            }
}
