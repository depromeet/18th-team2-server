package com.team2.server.party.api

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class CharacterControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
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
        fun `인증 없이 캐릭터 목록 조회 성공`() {
            val character1 = characterRepository.save(Character(name = "character1"))
            val character2 = characterRepository.save(Character(name = "character2"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/character1.jpg",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character1.id,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/character2.jpg",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character2.id,
                ),
            )

            mockMvc.get("/api/v1/characters").andExpect {
                status { isOk() }
                jsonPath("$.status") { value(200) }
                jsonPath("$.data[0].characterId") { value(character1.id) }
                jsonPath("$.data[0].name") { value("character1") }
                jsonPath("$.data[0].characterImageUrl") { value("/images/characters/character1.jpg") }
                jsonPath("$.data[1].characterId") { value(character2.id) }
                jsonPath("$.data[1].name") { value("character2") }
                jsonPath("$.data[1].characterImageUrl") { value("/images/characters/character2.jpg") }
            }
        }

        @Test
        fun `캐릭터 이미지가 없으면 url 없이 응답`() {
            val character = characterRepository.save(Character(name = "character-without-image"))

            mockMvc.get("/api/v1/characters").andExpect {
                status { isOk() }
                jsonPath("$.data[0].characterId") { value(character.id) }
                jsonPath("$.data[0].name") { value("character-without-image") }
                jsonPath("$.data[0].characterImageUrl") { doesNotExist() }
            }
        }

        @Test
        fun `캐릭터 정적 이미지 경로는 인증 없이 접근 가능`() {
            mockMvc.get("/images/characters/character1.jpg").andExpect {
                status { isOk() }
            }
        }
    }
