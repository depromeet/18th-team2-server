package com.team2.server.party.service

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.repository.CharacterRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DefaultCharacterInitializer(
    private val characterRepository: CharacterRepository,
    private val imageRepository: ImageRepository,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        initialize()
    }

    fun initialize() {
        DEFAULT_CHARACTERS.forEach { defaultCharacter ->
            val character = findOrCreateCharacter(defaultCharacter)
            ensureCharacterImage(character, defaultCharacter.imageUrl)
        }
    }

    private fun findOrCreateCharacter(defaultCharacter: DefaultCharacter): Character {
        val character = characterRepository.findByName(defaultCharacter.name)
        if (character != null) {
            if (character.imageUrl.isBlank()) {
                character.imageUrl = defaultCharacter.imageUrl
            }
            return character
        }

        return try {
            characterRepository.saveAndFlush(
                Character(
                    name = defaultCharacter.name,
                    imageUrl = defaultCharacter.imageUrl,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            if (!e.isConstraintViolation(AVATAR_NAME_UNIQUE_CONSTRAINT)) {
                throw e
            }
            characterRepository.findByName(defaultCharacter.name) ?: throw e
        }
    }

    private fun ensureCharacterImage(
        character: Character,
        imageUrl: String,
    ) {
        val characterImage =
            imageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                ImageTargetType.CHARACTER,
                character.id,
            )
        if (characterImage == null) {
            try {
                imageRepository.saveAndFlush(
                    Image(
                        imageUrl = imageUrl,
                        targetType = ImageTargetType.CHARACTER,
                        targetId = character.id,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                if (!e.isConstraintViolation(IMAGE_TARGET_SORT_UNIQUE_CONSTRAINT)) {
                    throw e
                }
            }
        }
    }

    private fun DataIntegrityViolationException.isConstraintViolation(constraintName: String): Boolean {
        val message =
            listOfNotNull(
                message,
                rootCause?.message,
                mostSpecificCause.message,
            ).joinToString(" ")
        return message.contains(constraintName, ignoreCase = true)
    }

    private data class DefaultCharacter(
        val name: String,
        val imageUrl: String,
    )

    companion object {
        private const val AVATAR_NAME_UNIQUE_CONSTRAINT = "uk_avatar_name"
        private const val IMAGE_TARGET_SORT_UNIQUE_CONSTRAINT = "uk_image_target_sort"

        private val DEFAULT_CHARACTERS =
            listOf(
                DefaultCharacter("character1", "/images/characters/character1.jpg"),
                DefaultCharacter("character2", "/images/characters/character2.jpg"),
                DefaultCharacter("character3", "/images/characters/character3.jpg"),
            )
    }
}
