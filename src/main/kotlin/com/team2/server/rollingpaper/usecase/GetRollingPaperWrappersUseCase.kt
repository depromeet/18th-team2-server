package com.team2.server.rollingpaper.usecase

import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.rollingpaper.dto.RollingPaperWrapperResult
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetRollingPaperWrappersUseCase(
    private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
    private val imageUrlReader: ImageUrlReader,
) {
    @Transactional(readOnly = true)
    fun getWrappers(): List<RollingPaperWrapperResult> {
        val wrappers = rollingPaperWrapperRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
        if (wrappers.isEmpty()) {
            return emptyList()
        }
        val imageUrlByTargetId =
            imageUrlReader.findFirstImageUrlByTargetIds(
                ImageTargetType.ROLLING_PAPER_WRAPPER,
                wrappers.map { it.id },
            )

        return wrappers.map { wrapper ->
            RollingPaperWrapperResult(
                wrapperId = wrapper.id,
                name = wrapper.name,
                wrapperImageUrl = imageUrlByTargetId[wrapper.id],
            )
        }
    }
}
