package com.team2.server.auth.oauth2.attributes

import com.team2.server.user.entity.AuthProvider

class KakaoAttributes(raw: Map<String, Any>) : OAuth2Attributes {
    override val provider: AuthProvider = AuthProvider.KAKAO
    override val providerId: String = raw["id"].toString()

    private val account: Map<String, Any> =
        @Suppress("UNCHECKED_CAST")
        (raw["kakao_account"] as? Map<String, Any>) ?: emptyMap()

    private val profile: Map<String, Any> =
        @Suppress("UNCHECKED_CAST")
        (account["profile"] as? Map<String, Any>) ?: emptyMap()

    override val email: String = (account["email"] as? String) ?: "$providerId@kakao.local"
    override val nickname: String = (profile["nickname"] as? String) ?: "사용자$providerId"
}
