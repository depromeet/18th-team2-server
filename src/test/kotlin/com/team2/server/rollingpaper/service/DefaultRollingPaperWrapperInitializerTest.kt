package com.team2.server.rollingpaper.service

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.function.Consumer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest
class DefaultRollingPaperWrapperInitializerTest
    @Autowired
    constructor(
        private val initializer: DefaultRollingPaperWrapperInitializer,
        private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
        private val participantRepository: ParticipantRepository,
        private val imageRepository: ImageRepository,
    ) {
        @BeforeEach
        fun setUp() {
            participantRepository.deleteAll()
            imageRepository.deleteAll()
            rollingPaperWrapperRepository.deleteAll()
        }

        @Test
        fun `기본 래퍼와 이미지를 생성한다`() {
            initializer.initialize()

            val candle = assertNotNull(rollingPaperWrapperRepository.findByName("Topping_Candle"))
            val cherry = assertNotNull(rollingPaperWrapperRepository.findByName("Topping_Cherry"))
            val strawberry =
                assertNotNull(rollingPaperWrapperRepository.findByName("Topping_Strawberry"))

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
            assertEquals(3, imageRepository.countByTargetType(ImageTargetType.ROLLING_PAPER_WRAPPER))
        }

        private fun findWrapperImageUrl(wrapperId: Long): String? =
            imageRepository
                .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                    ImageTargetType.ROLLING_PAPER_WRAPPER,
                    wrapperId,
                )?.imageUrl
    }

class DefaultRollingPaperWrapperInitializerUnitTest {
    private val rollingPaperWrapperRepository = mock<RollingPaperWrapperRepository>()
    private val imageRepository = mock<ImageRepository>()
    private val transactionTemplate = immediateTransactionTemplate()
    private val initializer =
        DefaultRollingPaperWrapperInitializer(
            rollingPaperWrapperRepository = rollingPaperWrapperRepository,
            imageRepository = imageRepository,
            transactionTemplate = transactionTemplate,
        )

    @Test
    fun `래퍼 생성 중 unique 제약 충돌은 기존 래퍼를 다시 조회한다`() {
        val existingWrapper = RollingPaperWrapper(name = "Topping_Candle")
        whenever(rollingPaperWrapperRepository.findByName(any()))
            .thenReturn(null, existingWrapper)
        whenever(rollingPaperWrapperRepository.saveAndFlush(any<RollingPaperWrapper>()))
            .thenThrow(DataIntegrityViolationException("duplicate key uk_rolling_paper_wrapper_name"))
        whenever(
            imageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                0L,
            ),
        ).thenReturn(
            Image(
                imageUrl = "/images/rolling-paper-wrappers/Topping_Candle.svg",
                targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                targetId = 0L,
            ),
        )

        initializer.initialize()
    }

    @Test
    fun `래퍼 이미지 생성 중 unique 제약 충돌은 무시한다`() {
        whenever(rollingPaperWrapperRepository.findByName(any())).thenReturn(null)
        whenever(rollingPaperWrapperRepository.saveAndFlush(any<RollingPaperWrapper>()))
            .thenAnswer { it.arguments[0] }
        whenever(
            imageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                0L,
            ),
        ).thenReturn(null)
        whenever(imageRepository.saveAndFlush(any()))
            .thenThrow(DataIntegrityViolationException("duplicate key uk_image_target_sort"))

        initializer.initialize()
    }

    @Test
    fun `래퍼 이미지 생성 중 unique 제약 외 오류는 다시 던진다`() {
        whenever(rollingPaperWrapperRepository.findByName(any())).thenReturn(null)
        whenever(rollingPaperWrapperRepository.saveAndFlush(any<RollingPaperWrapper>()))
            .thenAnswer { it.arguments[0] }
        whenever(
            imageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                0L,
            ),
        ).thenReturn(null)
        whenever(imageRepository.saveAndFlush(any()))
            .thenThrow(DataIntegrityViolationException("other constraint"))

        assertFailsWith<DataIntegrityViolationException> {
            initializer.initialize()
        }
    }

    @Test
    fun `애플리케이션 준비 이벤트 초기화 실패는 전파하지 않는다`() {
        val failingTransactionTemplate = mock<TransactionTemplate>()
        doThrow(RuntimeException("initialize failed"))
            .whenever(failingTransactionTemplate)
            .executeWithoutResult(any<Consumer<TransactionStatus>>())
        val failingInitializer =
            DefaultRollingPaperWrapperInitializer(
                rollingPaperWrapperRepository = rollingPaperWrapperRepository,
                imageRepository = imageRepository,
                transactionTemplate = failingTransactionTemplate,
            )

        failingInitializer.initializeOnReady()
    }

    private fun immediateTransactionTemplate(): TransactionTemplate {
        val transactionTemplate = mock<TransactionTemplate>()
        doAnswer { invocation ->
            invocation
                .getArgument<Consumer<TransactionStatus>>(0)
                .accept(SimpleTransactionStatus())
            null
        }.whenever(transactionTemplate).executeWithoutResult(any<Consumer<TransactionStatus>>())
        return transactionTemplate
    }
}
