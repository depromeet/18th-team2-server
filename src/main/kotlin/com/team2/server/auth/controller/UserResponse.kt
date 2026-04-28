package com.team2.server.auth.controller

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val provider: AuthProvider,
    val birthDay: String,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = user.id,
                name = user.name,
                email = user.email,
                provider = user.provider,
                birthDay = user.birthDay,
            )
    }
}
