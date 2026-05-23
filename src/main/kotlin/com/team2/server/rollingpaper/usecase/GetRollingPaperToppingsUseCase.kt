package com.team2.server.rollingpaper.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.rollingpaper.application.dto.RollingPaperToppingResult
import com.team2.server.rollingpaper.repository.RollingPaperToppingRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetRollingPaperToppingsUseCase(
    private val rollingPaperToppingRepository: RollingPaperToppingRepository,
    private val imageUrlReader: ImageUrlReader,
) {
    @Transactional(readOnly = true)
    fun getToppings(): List<RollingPaperToppingResult> {
        val toppings = rollingPaperToppingRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
        if (toppings.isEmpty()) {
            return emptyList()
        }
        val imageUrlByTargetId =
            imageUrlReader.findFirstImageUrlByTargetIds(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                toppings.map { it.id },
            )

        return toppings.map { topping ->
            RollingPaperToppingResult(
                toppingId = topping.id,
                name = topping.name,
                toppingImageUrl = requireToppingImageUrl(imageUrlByTargetId, topping.id),
            )
        }
    }

    private fun requireToppingImageUrl(
        imageUrlByToppingId: Map<Long, String>,
        toppingId: Long,
    ): String =
        imageUrlByToppingId[toppingId]
            ?: throw BusinessException(ErrorCode.INTERNAL_SERVER_ERROR)
}
