package com.team2.server.user.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class User(
    @Column(nullable = false)
    var name: String,
    @Column(name = "birth_day", nullable = false, length = 5)
    var birthDay: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    var provider: AuthProvider,
    @Column(nullable = false)
    var email: String,
) : BaseEntity()

enum class AuthProvider {
    KAKAO,
    GOOGLE,
    APPLE,
    NAVER,
}
