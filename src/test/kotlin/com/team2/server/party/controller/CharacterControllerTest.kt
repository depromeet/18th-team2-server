package com.team2.server.party.controller

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
            val defaultCharacter = characterRepository.save(Character(name = "Default"))
            val girlCharacter = characterRepository.save(Character(name = "Girl"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/Type=Default, Shape=Default.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = defaultCharacter.id,
                    sortOrder = 0,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/character-thumbnails/Type=Default, Shape=Circle.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = defaultCharacter.id,
                    sortOrder = 1,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/Type=Girl, Shape=Default.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = girlCharacter.id,
                    sortOrder = 0,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/character-thumbnails/Type=Girl, Shape=Circle.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = girlCharacter.id,
                    sortOrder = 1,
                ),
            )

            mockMvc.get("/api/v1/characters").andExpect {
                status { isOk() }
                jsonPath("$.status") { value(200) }
                jsonPath("$.data[0].characterId") { value(defaultCharacter.id) }
                jsonPath("$.data[0].name") { value("Default") }
                jsonPath("$.data[0].characterImageUrl") {
                    value("/images/characters/Type=Default, Shape=Default.png")
                }
                jsonPath("$.data[0].characterThumbnailImageUrl") {
                    value("/images/character-thumbnails/Type=Default, Shape=Circle.png")
                }
                jsonPath("$.data[1].characterId") { value(girlCharacter.id) }
                jsonPath("$.data[1].name") { value("Girl") }
                jsonPath("$.data[1].characterImageUrl") {
                    value("/images/characters/Type=Girl, Shape=Default.png")
                }
                jsonPath("$.data[1].characterThumbnailImageUrl") {
                    value("/images/character-thumbnails/Type=Girl, Shape=Circle.png")
                }
            }
        }

        @Test
        fun `캐릭터 이미지가 없으면 이미지 url 없이 응답`() {
            val character = characterRepository.save(Character(name = "character-without-image"))

            mockMvc.get("/api/v1/characters").andExpect {
                status { isOk() }
                jsonPath("$.data[0].characterId") { value(character.id) }
                jsonPath("$.data[0].name") { value("character-without-image") }
                jsonPath("$.data[0].characterImageUrl") { doesNotExist() }
                jsonPath("$.data[0].characterThumbnailImageUrl") { doesNotExist() }
            }
        }

        @Test
        fun `캐릭터 정적 이미지 경로는 인증 없이 접근 가능`() {
            mockMvc.get("/images/characters/Type=Default, Shape=Default.png").andExpect {
                status { isOk() }
            }
            mockMvc.get("/images/character-thumbnails/Type=Default, Shape=Circle.png").andExpect {
                status { isOk() }
            }
        }
    }
