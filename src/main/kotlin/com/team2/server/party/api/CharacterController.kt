package com.team2.server.party.api

import com.team2.server.common.web.ApiResponse
import com.team2.server.party.application.dto.CharacterResult
import com.team2.server.party.application.usecase.GetCharactersUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/characters")
class CharacterController(
    private val getCharactersUseCase: GetCharactersUseCase,
) : CharacterApi {
    @GetMapping
    override fun getCharacters(): ApiResponse<List<CharacterResult>> =
        ApiResponse.success(getCharactersUseCase.invoke())
}
