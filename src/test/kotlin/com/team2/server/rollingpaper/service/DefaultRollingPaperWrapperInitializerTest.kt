package com.team2.server.rollingpaper.service

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class DefaultRollingPaperWrapperInitializerTest
    @Autowired
    constructor(
        private val initializer: DefaultRollingPaperWrapperInitializer,
        private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
        private val rollingPaperRepository: RollingPaperRepository,
        private val participantRepository: ParticipantRepository,
        private val imageRepository: ImageRepository,
    ) {
        @BeforeEach
        fun setUp() {
            rollingPaperRepository.deleteAll()
            participantRepository.deleteAll()
            imageRepository.deleteAll()
            rollingPaperWrapperRepository.deleteAll()
        }

        @Test
        fun `기본 래퍼와 이미지를 생성한다`() {
            initializer.initialize()

            val candle = assertNotNull(rollingPaperWrapperRepository.findFirstByNameOrderByIdAsc("Topping_Candle"))
            val cherry = assertNotNull(rollingPaperWrapperRepository.findFirstByNameOrderByIdAsc("Topping_Cherry"))
            val strawberry =
                assertNotNull(rollingPaperWrapperRepository.findFirstByNameOrderByIdAsc("Topping_Strawberry"))

            assertEquals(
                "/images/rolling-paper-wrappers/Topping_Candle.svg",
                findWrapperImageUrl(candle.id),
            )
            assertEquals(
                "/images/rolling-paper-wrappers/Topping_Cherry.svg",
                findWrapperImageUrl(cherry.id),
            )
            assertEquals(
                "/images/rolling-paper-wrappers/Topping_Strawberry.svg",
                findWrapperImageUrl(strawberry.id),
            )
        }

        @Test
        fun `이미 존재하는 기본 래퍼와 이미지는 중복 생성하지 않는다`() {
            initializer.initialize()
            initializer.initialize()

            assertEquals(3, rollingPaperWrapperRepository.count())
            assertEquals(3, imageRepository.count())
        }

        private fun findWrapperImageUrl(wrapperId: Long): String? =
            imageRepository
                .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                    ImageTargetType.ROLLING_PAPER_WRAPPER,
                    wrapperId,
                )?.imageUrl
    }
