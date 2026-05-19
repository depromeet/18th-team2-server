package com.team2.server.me.api.dto

import com.team2.server.me.application.dto.MeAccountResult
import com.team2.server.user.entity.AuthProvider
import java.time.LocalDate

data class MeAccountResponse(
    val nickname: String,
    val provider: AuthProvider,
    val connectedAt: LocalDate,
    val supportChatUrl: String,
) {
    companion object {
        fun from(result: MeAccountResult): MeAccountResponse =
            MeAccountResponse(
                nickname = result.nickname,
                provider = result.provider,
                connectedAt = result.connectedAt,
                supportChatUrl = result.supportChatUrl,
            )
    }
}
