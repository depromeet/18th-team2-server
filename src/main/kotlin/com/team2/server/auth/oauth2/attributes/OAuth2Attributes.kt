package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider

interface OAuth2Attributes {
    val provider: AuthProvider
    val providerId: String
    val email: String
    val nickname: String
}
