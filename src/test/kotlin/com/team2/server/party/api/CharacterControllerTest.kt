package com.team2.server.party.api

import com.team2.server.common.DatabaseCleanup
import com.team2.server.common.image.entity.Image
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CharacterControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val characterRepository: CharacterRepository,
        private val imageRepository: ImageRepository,
        private val databaseCleanup: DatabaseCleanup,
    ) {
        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `인증 없이 캐릭터 목록 조회 성공`() {
            val blueCharacter = characterRepository.save(Character(name = "blue"))
            val greenCharacter = characterRepository.save(Character(name = "green"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/blue.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = blueCharacter.id,
                    sortOrder = 0,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/character-thumbnails/blue.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = blueCharacter.id,
                    sortOrder = 1,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/characters/green.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = greenCharacter.id,
                    sortOrder = 0,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/character-thumbnails/green.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = greenCharacter.id,
                    sortOrder = 1,
                ),
            )

            mockMvc.get("/api/v1/characters").andExpect {
                status { isOk() }
                jsonPath("$.status") { value(200) }
                jsonPath("$.data[0].characterId") { value(blueCharacter.id) }
                jsonPath("$.data[0].name") { value("blue") }
                jsonPath("$.data[0].characterImageUrl") {
                    value("/images/characters/blue.png")
                }
                jsonPath("$.data[0].characterThumbnailImageUrl") {
                    value("/images/character-thumbnails/blue.png")
                }
                jsonPath("$.data[1].characterId") { value(greenCharacter.id) }
                jsonPath("$.data[1].name") { value("green") }
                jsonPath("$.data[1].characterImageUrl") {
                    value("/images/characters/green.png")
                }
                jsonPath("$.data[1].characterThumbnailImageUrl") {
                    value("/images/character-thumbnails/green.png")
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
            mockMvc.get("/images/characters/blue.png").andExpect {
                status { isOk() }
            }
            mockMvc.get("/images/characters/party-hat/blue.png").andExpect {
                status { isOk() }
            }
            mockMvc.get("/images/character-thumbnails/blue.png").andExpect {
                status { isOk() }
            }
        }
    }
