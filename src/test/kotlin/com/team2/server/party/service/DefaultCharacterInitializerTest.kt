package com.team2.server.party.service

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class DefaultCharacterInitializerTest
    @Autowired
    constructor(
        private val initializer: DefaultCharacterInitializer,
        private val characterRepository: CharacterRepository,
        private val imageRepository: ImageRepository,
        private val participantRepository: ParticipantRepository,
    ) {
        @BeforeEach
        fun setUp() {
            participantRepository.deleteAll()
            imageRepository.deleteAll()
            characterRepository.deleteAll()
        }

        @Test
        fun `기본 캐릭터가 없으면 캐릭터와 이미지 데이터를 생성`() {
            initializer.initialize()

            val defaultCharacter = assertNotNull(characterRepository.findByName("Default"))
            val girlCharacter = assertNotNull(characterRepository.findByName("Girl"))
            val chocoCharacter = assertNotNull(characterRepository.findByName("Choco"))
            val cloudCharacter = assertNotNull(characterRepository.findByName("Cloud"))
            val candleCharacter = assertNotNull(characterRepository.findByName("Candle"))
            assertEquals(5, characterRepository.count())
            assertEquals(10, imageRepository.count())
            assertCharacterImages(
                defaultCharacter.id,
                "/images/characters/Type=Default, Shape=Default.png",
                "/images/character-thumbnails/Type=Default, Shape=Circle.png",
            )
            assertCharacterImages(
                girlCharacter.id,
                "/images/characters/Type=Girl, Shape=Default.png",
                "/images/character-thumbnails/Type=Girl, Shape=Circle.png",
            )
            assertCharacterImages(
                chocoCharacter.id,
                "/images/characters/Type=Choco, Shape=Default.png",
                "/images/character-thumbnails/Type=Choco, Shape=Circle.png",
            )
            assertCharacterImages(
                cloudCharacter.id,
                "/images/characters/Type=Cloud, Shape=Default.png",
                "/images/character-thumbnails/Type=Cloud, Shape=Circle.png",
            )
            assertCharacterImages(
                candleCharacter.id,
                "/images/characters/Type=Candle, Shape=Default.png",
                "/images/character-thumbnails/Type=Candle, Shape=Circle.png",
            )
        }

        @Test
        fun `초기화를 반복해도 기본 캐릭터와 이미지가 중복 생성되지 않음`() {
            initializer.initialize()
            initializer.initialize()

            assertEquals(5, characterRepository.count())
            assertEquals(10, imageRepository.count())
        }

        @Test
        fun `기존 기본 캐릭터가 있으면 새 이름과 이미지 경로로 갱신`() {
            val legacyCharacter = characterRepository.save(Character(name = "character1"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/character1.jpg",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = legacyCharacter.id,
                    sortOrder = 0,
                ),
            )

            initializer.initialize()

            val defaultCharacter = assertNotNull(characterRepository.findByName("Default"))
            assertEquals(legacyCharacter.id, defaultCharacter.id)
            assertCharacterImages(
                defaultCharacter.id,
                "/images/characters/Type=Default, Shape=Default.png",
                "/images/character-thumbnails/Type=Default, Shape=Circle.png",
            )
        }

        private fun assertCharacterImages(
            characterId: Long,
            imageUrl: String,
            thumbnailImageUrl: String,
        ) {
            assertEquals(imageUrl, findCharacterImageUrl(characterId, 0))
            assertEquals(thumbnailImageUrl, findCharacterImageUrl(characterId, 1))
        }

        private fun findCharacterImageUrl(
            characterId: Long,
            sortOrder: Int,
        ): String? =
            imageRepository
                .findByTargetTypeAndTargetIdAndSortOrder(ImageTargetType.CHARACTER, characterId, sortOrder)
                ?.imageUrl
    }
