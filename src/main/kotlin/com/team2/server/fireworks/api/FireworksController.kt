package com.team2.server.fireworks.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.fireworks.application.usecase.TriggerFireworksUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class FireworksController(
    private val triggerFireworksUseCase: TriggerFireworksUseCase,
) : FireworksApi {
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/api/v1/parties/{partyId}/fireworks")
    override fun triggerFireworks(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ) {
        triggerFireworksUseCase.invoke(partyId, principal?.userId, participantToken)
    }
}
