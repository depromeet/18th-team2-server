package com.team2.server.rollingpaper.controller

import com.team2.server.common.DatabaseCleanup
import com.team2.server.common.image.entity.Image
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.rollingpaper.entity.RollingPaperTopping
import com.team2.server.rollingpaper.repository.RollingPaperToppingRepository
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
class RollingPaperToppingControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val rollingPaperToppingRepository: RollingPaperToppingRepository,
        private val imageRepository: ImageRepository,
        private val databaseCleanup: DatabaseCleanup,
    ) {
        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        @Test
        fun `인증 없이 토핑 목록 조회 성공`() {
            val topping1 = rollingPaperToppingRepository.save(RollingPaperTopping(name = "Topping_Candle"))
            val topping2 = rollingPaperToppingRepository.save(RollingPaperTopping(name = "Topping_Cherry"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/Topping_Candle.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = topping1.id,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/Topping_Cherry.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = topping2.id,
                ),
            )

            mockMvc.get("/api/v1/rolling-paper-toppings").andExpect {
                status { isOk() }
                jsonPath("$.status") { value(200) }
                jsonPath("$.data.length()") { value(2) }
                jsonPath("$.data[0].toppingId") { value(topping1.id) }
                jsonPath("$.data[0].name") { value("Topping_Candle") }
                jsonPath("$.data[0].toppingImageUrl") {
                    value("/images/rolling-paper-wrappers/Topping_Candle.svg")
                }
                jsonPath("$.data[1].toppingId") { value(topping2.id) }
                jsonPath("$.data[1].name") { value("Topping_Cherry") }
                jsonPath("$.data[1].toppingImageUrl") {
                    value("/images/rolling-paper-wrappers/Topping_Cherry.svg")
                }
            }
        }

        @Test
        fun `이미지가 여러 개이면 sortOrder가 가장 작은 이미지 url 응답`() {
            val topping = rollingPaperToppingRepository.save(RollingPaperTopping(name = "Topping_Multi_Image"))
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/second.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = topping.id,
                    sortOrder = 1,
                ),
            )
            imageRepository.save(
                Image(
                    imageUrl = "/images/rolling-paper-wrappers/first.svg",
                    targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                    targetId = topping.id,
                    sortOrder = 0,
                ),
            )

            mockMvc.get("/api/v1/rolling-paper-toppings").andExpect {
                status { isOk() }
                jsonPath("$.data[0].toppingImageUrl") {
                    value("/images/rolling-paper-wrappers/first.svg")
                }
            }
        }

        @Test
        fun `잘못된 Bearer 토큰이면 공개 토핑 조회 API도 401`() {
            mockMvc
                .get("/api/v1/rolling-paper-toppings") {
                    header("Authorization", "Bearer not-a-jwt")
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }

        @Test
        fun `토핑 정적 이미지 경로는 인증 없이 접근 가능`() {
            mockMvc.get("/images/rolling-paper-wrappers/Topping_Candle.svg").andExpect {
                status { isOk() }
            }
        }
    }
