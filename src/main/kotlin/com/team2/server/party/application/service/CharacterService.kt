package com.team2.server.party.application.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CharacterResult
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.infrastructure.CharacterImageResolver
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import org.springframework.stereotype.Service

@Service
class CharacterService(
    private val characterRepository: CharacterRepository,
    private val characterImageResolver: CharacterImageResolver,
) {
    fun requireCharacter(characterId: Long?): Character {
        val id = characterId ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        return characterRepository
            .findById(id)
            .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
    }

    fun toResult(character: Character): CharacterResult =
        CharacterResult(
            characterId = character.id,
            name = character.name,
            characterImageUrl = characterImageResolver.resolve(character),
            characterThumbnailImageUrl = null,
        )
}
