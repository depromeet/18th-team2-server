package com.team2.server.party.controller

import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.party.dto.CharacterResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Character", description = "캐릭터 API")
interface CharacterApi {
    @Operation(
        summary = "캐릭터 목록 조회",
        description = "파티 참여 시 선택 가능한 캐릭터 목록을 조회한다.",
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "캐릭터 목록 조회 성공",
    )
    @InternalServerErrorResponse
    fun getCharacters(): ApiResponse<List<CharacterResponse>>
}
