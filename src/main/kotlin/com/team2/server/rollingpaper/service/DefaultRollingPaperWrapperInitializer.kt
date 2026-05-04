package com.team2.server.rollingpaper.service

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DefaultRollingPaperWrapperInitializer(
    private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
    private val imageRepository: ImageRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun initializeOnReady() {
        runCatching { initialize() }
            .onFailure { log.warn("Failed to initialize default rolling paper wrappers", it) }
    }

    @Transactional
    fun initialize() {
        DEFAULT_WRAPPERS.forEach { defaultWrapper ->
            val wrapper = findOrCreateWrapper(defaultWrapper)
            ensureWrapperImage(wrapper, defaultWrapper.imageUrl)
        }
    }

    private fun findOrCreateWrapper(defaultWrapper: DefaultWrapper): RollingPaperWrapper {
        val wrapper = rollingPaperWrapperRepository.findFirstByNameOrderByIdAsc(defaultWrapper.name)
        if (wrapper != null) {
            return wrapper
        }

        return rollingPaperWrapperRepository.save(
            RollingPaperWrapper(name = defaultWrapper.name),
        )
    }

    private fun ensureWrapperImage(
        wrapper: RollingPaperWrapper,
        imageUrl: String,
    ) {
        val wrapperImage =
            imageRepository.findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                wrapper.id,
            )
        if (wrapperImage == null) {
            try {
                imageRepository.saveAndFlush(
                    Image(
                        imageUrl = imageUrl,
                        targetType = ImageTargetType.ROLLING_PAPER_WRAPPER,
                        targetId = wrapper.id,
                    ),
                )
            } catch (e: DataIntegrityViolationException) {
                if (!e.isConstraintViolation(IMAGE_TARGET_SORT_UNIQUE_CONSTRAINT)) {
                    throw e
                }
            }
        }
    }

    private fun DataIntegrityViolationException.isConstraintViolation(constraintName: String): Boolean {
        val message =
            listOfNotNull(
                message,
                rootCause?.message,
                mostSpecificCause.message,
            ).joinToString(" ")
        return message.contains(constraintName, ignoreCase = true)
    }

    private data class DefaultWrapper(
        val name: String,
        val imageUrl: String,
    )

    companion object {
        private const val IMAGE_TARGET_SORT_UNIQUE_CONSTRAINT = "uk_image_target_sort"

        private val DEFAULT_WRAPPERS =
            listOf(
                DefaultWrapper("Topping_Candle", "/images/rolling-paper-wrappers/Topping_Candle.svg"),
                DefaultWrapper("Topping_Cherry", "/images/rolling-paper-wrappers/Topping_Cherry.svg"),
                DefaultWrapper("Topping_Strawberry", "/images/rolling-paper-wrappers/Topping_Strawberry.svg"),
            )
    }
}
