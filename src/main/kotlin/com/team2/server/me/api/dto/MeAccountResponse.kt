package com.team2.server.me.api.dto

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import java.time.LocalDate

data class MeAccountResponse(
    val nickname: String,
    val provider: AuthProvider,
    val connectedAt: LocalDate,
    val supportChatUrl: String,
) {
    companion object {
        fun from(
            user: User,
            supportChatUrl: String,
        ): MeAccountResponse =
            MeAccountResponse(
                nickname = user.name,
                provider = user.provider,
                connectedAt = user.createdAt.toLocalDate(),
                supportChatUrl = supportChatUrl,
            )
    }
}
