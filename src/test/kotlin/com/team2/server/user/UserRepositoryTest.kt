package com.team2.server.user

import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DataJpaTest
class UserRepositoryTest
    @Autowired
    constructor(
        private val userRepository: UserRepository,
    ) {
        private fun newUser(
            providerId: String,
            email: String = "$providerId@kakao.local",
        ) = User(
            name = "닉",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = providerId,
            email = email,
        )

        @Test
        fun `findByProviderAndProviderId 매칭 사용자 반환`() {
            val saved = userRepository.save(newUser("kakao-1"))

            val found = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-1")

            assertNotNull(found)
            assertEquals(saved.id, found.id)
        }

        @Test
        fun `findByProviderAndProviderId 미매칭 시 null`() {
            val found = userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "missing")
            assertNull(found)
        }

        @Test
        fun `provider provider_id 복합 unique 제약 위반 시 예외`() {
            userRepository.saveAndFlush(newUser("dup", "a@kakao.local"))

            assertThrows<DataIntegrityViolationException> {
                userRepository.saveAndFlush(newUser("dup", "b@kakao.local"))
            }
        }
    }
