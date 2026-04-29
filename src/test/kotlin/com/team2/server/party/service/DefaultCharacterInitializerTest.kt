package com.team2.server.party.service

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.repository.CharacterRepository
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
    ) {
        @BeforeEach
        fun setUp() {
            imageRepository.deleteAll()
            characterRepository.deleteAll()
        }

        @Test
        fun `기본 캐릭터가 없으면 이름과 이미지 URL로 생성`() {
            initializer.initialize()

            val character1 = assertNotNull(characterRepository.findByName("character1"))
            val character2 = assertNotNull(characterRepository.findByName("character2"))
            val character3 = assertNotNull(characterRepository.findByName("character3"))
            assertEquals(3, characterRepository.count())
            assertEquals("/images/characters/character1.jpg", character1.imageUrl)
            assertEquals("/images/characters/character1.jpg", findCharacterImageUrl(character1.id))
            assertEquals("/images/characters/character2.jpg", findCharacterImageUrl(character2.id))
            assertEquals("/images/characters/character3.jpg", findCharacterImageUrl(character3.id))
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
    }
