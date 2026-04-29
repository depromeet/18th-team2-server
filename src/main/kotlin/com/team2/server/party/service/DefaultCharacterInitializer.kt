package com.team2.server.party.service

import com.team2.server.common.entity.BaseEntity
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.repository.CharacterRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DefaultCharacterInitializer(
    private val characterRepository: CharacterRepository,
    private val imageRepository: ImageRepository,
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        initialize()
    }

    fun initialize() {
        DEFAULT_CHARACTERS.forEach { defaultCharacter ->
            val character =
                characterRepository
                    .findById(defaultCharacter.id)
                    .orElseGet { createCharacter(defaultCharacter) }

            if (imageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(ImageTargetType.CHARACTER, character.id) == null) {
                createCharacterImage(defaultCharacter)
            }
        }
    }

    private fun createCharacter(defaultCharacter: DefaultCharacter): Character {
        val character = Character(name = defaultCharacter.name)
        idField.set(character, defaultCharacter.id)
        if (hasAvatarImageUrlColumn()) {
            insertCharacterWithImageUrl(character, defaultCharacter.imageUrl)
        } else {
            insertCharacter(character)
        }
        return character
    }

    private fun hasAvatarImageUrlColumn(): Boolean =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE LOWER(TABLE_NAME) = 'avatar'
              AND LOWER(COLUMN_NAME) = 'image_url'
            """.trimIndent(),
            Long::class.java,
        ) != 0L

    private fun insertCharacter(character: Character) {
        jdbcTemplate.update(
            """
            INSERT INTO avatar (id, name, created_at, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            character.id,
            character.name,
        )
    }

    private fun insertCharacterWithImageUrl(
        character: Character,
        imageUrl: String,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO avatar (id, name, image_url, created_at, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            character.id,
            character.name,
            imageUrl,
        )
    }

    private fun createCharacterImage(defaultCharacter: DefaultCharacter) {
        jdbcTemplate.update(
            """
            INSERT INTO image (image_url, target_type, target_id, sort_order, created_at, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            defaultCharacter.imageUrl,
            ImageTargetType.CHARACTER.name,
            defaultCharacter.id,
            0,
        )
    }

    private data class DefaultCharacter(
        val id: Long,
        val name: String,
        val imageUrl: String,
    )

    companion object {
        private val idField =
            BaseEntity::class.java.getDeclaredField("id").apply {
                isAccessible = true
            }

        private val DEFAULT_CHARACTERS =
            listOf(
                DefaultCharacter(1L, "character1", "/images/characters/character1.jpg"),
                DefaultCharacter(2L, "character2", "/images/characters/character2.jpg"),
                DefaultCharacter(3L, "character3", "/images/characters/character3.jpg"),
            )
    }
}
