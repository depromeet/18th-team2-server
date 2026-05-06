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
            syncCharacterImage(character, defaultCharacter.imageUrl, CHARACTER_IMAGE_SORT_ORDER)
            syncCharacterImage(character, defaultCharacter.thumbnailImageUrl, CHARACTER_THUMBNAIL_IMAGE_SORT_ORDER)
        }
    }

    private fun findOrCreateCharacter(defaultCharacter: DefaultCharacter): Character =
        characterRepository.findByName(defaultCharacter.name)
            ?: renameLegacyCharacter(defaultCharacter)
            ?: createCharacter(defaultCharacter.name)

    private fun renameLegacyCharacter(defaultCharacter: DefaultCharacter): Character? {
        val legacyCharacter = defaultCharacter.legacyName?.let(characterRepository::findByName) ?: return null
        legacyCharacter.name = defaultCharacter.name
        return saveCharacter(legacyCharacter, defaultCharacter.name)
    }

    private fun createCharacter(name: String): Character = saveCharacter(Character(name = name), name)

    private fun saveCharacter(
        character: Character,
        name: String,
    ): Character =
        try {
            characterRepository.saveAndFlush(character)
        } catch (e: DataIntegrityViolationException) {
            findCharacterAfterUniqueConstraintViolation(e, name)
        }

    private fun findCharacterAfterUniqueConstraintViolation(
        exception: DataIntegrityViolationException,
        name: String,
    ): Character {
        if (!exception.isConstraintViolation(AVATAR_NAME_UNIQUE_CONSTRAINT)) {
            throw exception
        }
        return characterRepository.findByName(name) ?: throw exception
    }

    private fun syncCharacterImage(
        character: Character,
        imageUrl: String,
        sortOrder: Int,
    ) {
        if (updateCharacterImage(character, imageUrl, sortOrder)) {
            return
        }

        try {
            imageRepository.saveAndFlush(
                Image(
                    imageUrl = imageUrl,
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character.id,
                    sortOrder = sortOrder,
                ),
            )
        } catch (e: DataIntegrityViolationException) {
            recoverCharacterImageAfterUniqueConstraintViolation(e, character, imageUrl, sortOrder)
        }
    }

    private fun recoverCharacterImageAfterUniqueConstraintViolation(
        exception: DataIntegrityViolationException,
        character: Character,
        imageUrl: String,
        sortOrder: Int,
    ) {
        if (!exception.isConstraintViolation(IMAGE_TARGET_SORT_UNIQUE_CONSTRAINT)) {
            throw exception
        }
        if (!updateCharacterImage(character, imageUrl, sortOrder)) {
            throw IllegalStateException(
                "Failed to sync character image after unique constraint violation. " +
                    "characterId=${character.id}, imageUrl=$imageUrl, sortOrder=$sortOrder",
                exception,
            )
        }
    }

    private fun updateCharacterImage(
        character: Character,
        imageUrl: String,
        sortOrder: Int,
    ): Boolean =
        imageRepository
            .findByTargetTypeAndTargetIdAndSortOrder(
                ImageTargetType.CHARACTER,
                character.id,
                sortOrder,
            )?.let {
                if (it.imageUrl != imageUrl) {
                    it.imageUrl = imageUrl
                    imageRepository.saveAndFlush(it)
                }
                true
            } ?: false

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
        val thumbnailImageUrl: String,
        val legacyName: String? = null,
    )

    companion object {
        private const val AVATAR_NAME_UNIQUE_CONSTRAINT = "uk_avatar_name"
        private const val IMAGE_TARGET_SORT_UNIQUE_CONSTRAINT = "uk_image_target_sort"
        private const val CHARACTER_IMAGE_SORT_ORDER = 0
        private const val CHARACTER_THUMBNAIL_IMAGE_SORT_ORDER = 1

        private val DEFAULT_CHARACTERS =
            listOf(
                DefaultCharacter(
                    name = "Default",
                    imageUrl = "/images/characters/Type=Default, Shape=Default.png",
                    thumbnailImageUrl = "/images/character-thumbnails/Type=Default, Shape=Circle.png",
                    legacyName = "character1",
                ),
                DefaultCharacter(
                    name = "Girl",
                    imageUrl = "/images/characters/Type=Girl, Shape=Default.png",
                    thumbnailImageUrl = "/images/character-thumbnails/Type=Girl, Shape=Circle.png",
                    legacyName = "character2",
                ),
                DefaultCharacter(
                    name = "Choco",
                    imageUrl = "/images/characters/Type=Choco, Shape=Default.png",
                    thumbnailImageUrl = "/images/character-thumbnails/Type=Choco, Shape=Circle.png",
                    legacyName = "character3",
                ),
                DefaultCharacter(
                    name = "Cloud",
                    imageUrl = "/images/characters/Type=Cloud, Shape=Default.png",
                    thumbnailImageUrl = "/images/character-thumbnails/Type=Cloud, Shape=Circle.png",
                ),
                DefaultCharacter(
                    name = "Candle",
                    imageUrl = "/images/characters/Type=Candle, Shape=Default.png",
                    thumbnailImageUrl = "/images/character-thumbnails/Type=Candle, Shape=Circle.png",
                ),
            )
    }
}
