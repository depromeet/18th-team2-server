package com.team2.server.party.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "캐릭터 조회 응답")
data class CharacterResponse(
    @Schema(description = "캐릭터 ID. 파티 참여 요청의 characterId로 전달합니다.", example = "1")
    val characterId: Long,
    @Schema(description = "캐릭터 이름", example = "character1")
    val name: String,
    @Schema(description = "캐릭터 이미지 URL", example = "/images/characters/character1.jpg")
    val characterImageUrl: String?,
) {
    companion object {
        fun from(result: CharacterResult): CharacterResponse =
            CharacterResponse(
                characterId = result.characterId,
                name = result.name,
                characterImageUrl = result.characterImageUrl,
            )
    }
}
