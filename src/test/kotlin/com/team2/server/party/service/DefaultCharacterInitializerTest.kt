package com.team2.server.party.service

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.repository.CharacterRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class DefaultCharacterInitializerTest
    @Autowired
    constructor(
        private val initializer: DefaultCharacterInitializer,
        private val characterRepository: CharacterRepository,
        private val imageRepository: ImageRepository,
        private val jdbcTemplate: JdbcTemplate,
    ) {
        @BeforeEach
        fun setUp() {
            addLegacyAvatarImageUrlColumn()
            imageRepository.deleteAll()
            characterRepository.deleteAll()
        }

        @Test
        fun `기본 캐릭터가 없으면 고정 ID와 이미지 URL로 생성`() {
            initializer.initialize()

            assertTrue(characterRepository.findById(1L).isPresent)
            assertTrue(characterRepository.findById(2L).isPresent)
            assertTrue(characterRepository.findById(3L).isPresent)
            assertEquals(3, characterRepository.count())
            assertEquals("/images/characters/character1.jpg", findAvatarImageUrl(1L))
            assertEquals("/images/characters/character1.jpg", findCharacterImageUrl(1L))
            assertEquals("/images/characters/character2.jpg", findCharacterImageUrl(2L))
            assertEquals("/images/characters/character3.jpg", findCharacterImageUrl(3L))
        }

        @Test
        fun `초기화를 반복해도 기본 캐릭터와 이미지가 중복 생성되지 않음`() {
            initializer.initialize()
            initializer.initialize()

            assertEquals(3, characterRepository.count())
            assertEquals(3, imageRepository.count())
        }

        private fun findCharacterImageUrl(characterId: Long): String? =
            imageRepository
                .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(ImageTargetType.CHARACTER, characterId)
                ?.imageUrl

        private fun findAvatarImageUrl(characterId: Long): String? =
            jdbcTemplate.queryForObject(
                "SELECT image_url FROM avatar WHERE id = ?",
                String::class.java,
                characterId,
            )

        private fun addLegacyAvatarImageUrlColumn() {
            val exists =
                jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'AVATAR'
                      AND COLUMN_NAME = 'IMAGE_URL'
                    """.trimIndent(),
                    Long::class.java,
                ) ?: 0L

            if (exists == 0L) {
                jdbcTemplate.execute("ALTER TABLE avatar ADD COLUMN image_url VARCHAR(255) NOT NULL DEFAULT ''")
            }
        }
    }
