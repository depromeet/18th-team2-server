package com.team2.server.party.application.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "캐릭터 조회 응답")
data class CharacterResult(
    @Schema(description = "캐릭터 ID. 파티 참여 요청의 characterId로 전달합니다.", example = "1")
    val characterId: Long,
    @Schema(description = "캐릭터 이름", example = "Default")
    val name: String,
    @Schema(description = "캐릭터 이미지 URL", example = "/images/characters/blue.svg")
    val characterImageUrl: String?,
    @Schema(description = "캐릭터 썸네일 이미지 URL", example = "/images/character-thumbnails/Type=Default, Shape=Circle.png")
    val characterThumbnailImageUrl: String?,
)
