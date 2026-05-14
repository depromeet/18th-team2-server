package com.team2.server.party.application.dto

data class ParticipantRealtimeProfileResult(
    val participantId: Long,
    val isHost: Boolean,
    val nickname: String?,
    val character: CharacterResult?,
) {
    val nicknameEditable: Boolean get() = !isHost
}
