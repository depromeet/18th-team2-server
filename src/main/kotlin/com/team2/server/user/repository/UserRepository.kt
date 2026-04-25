package com.team2.server.user.repository

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByProviderAndProviderId(
        provider: AuthProvider,
        providerId: String,
    ): User?
}
