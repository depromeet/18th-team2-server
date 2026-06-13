package com.team2.server.party.infrastructure

import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageRepository
import com.team2.server.party.domain.entity.Character
import org.springframework.stereotype.Component

// 캐릭터 단건 이미지 조회용. 현재 사용처는 없으나, 추후 캐릭터 상세/공유 화면에서 활용 예정 — 제거 금지.
@Component
class CharacterImageResolver(
    private val imageRepository: ImageRepository,
) {
    fun resolve(character: Character): String? = resolve(character.id)

    fun resolve(characterId: Long): String? =
        imageRepository
            .findFirstByTargetTypeAndTargetIdOrderBySortOrderAsc(ImageTargetType.CHARACTER, characterId)
            ?.imageUrl
}
