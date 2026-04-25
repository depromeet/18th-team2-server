package com.team2.server.auth.jwt

import com.team2.server.auth.config.JwtProperties
import com.team2.server.user.entity.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val props: JwtProperties,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.secret))

    fun issue(user: User): String {
        val now = Instant.now()
        val exp = now.plus(props.expirationHours, ChronoUnit.HOURS)
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("provider", user.provider.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key, Jwts.SIG.HS256)
            .compact()
    }

    fun parse(token: String): Claims =
        Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
}
