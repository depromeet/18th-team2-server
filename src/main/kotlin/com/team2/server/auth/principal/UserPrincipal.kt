package com.team2.server.auth.principal

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

data class UserPrincipal(
    val userId: Long,
    val email: String,
    val provider: AuthProvider,
    private val attrs: Map<String, Any> = emptyMap(),
    private val authoritiesSet: Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER")),
) : OAuth2User, UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = authoritiesSet

    override fun getName(): String = userId.toString()

    override fun getAttributes(): Map<String, Any> = attrs

    override fun getPassword(): String? = null

    override fun getUsername(): String = userId.toString()

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    companion object {
        fun from(user: User, attrs: Map<String, Any> = emptyMap()): UserPrincipal =
            UserPrincipal(
                userId = user.id,
                email = user.email,
                provider = user.provider,
                attrs = attrs,
            )
    }
}
