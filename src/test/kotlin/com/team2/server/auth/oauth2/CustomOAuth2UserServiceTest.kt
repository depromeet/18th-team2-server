package com.team2.server.auth.oauth2

import com.team2.server.auth.FakeUserRepository
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.user.entity.AuthProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CustomOAuth2UserServiceTest {
    private fun service(): Pair<CustomOAuth2UserService, FakeUserRepository> {
        val repo = FakeUserRepository()
        return CustomOAuth2UserService(repo) to repo
    }

    private val rawKakao = mapOf<String, Any>(
        "id" to 12345L,
        "kakao_account" to mapOf(
            "email" to "u@kakao.com",
            "profile" to mapOf("nickname" to "홍길동"),
        ),
    )

    @Test
    fun `신규 사용자는 저장되고 UserPrincipal 반환`() {
        val (svc, repo) = service()

        val principal = svc.processOAuth2User("kakao", rawKakao) as UserPrincipal

        assertEquals(1, repo.all().size)
        val saved = repo.all().first()
        assertEquals("12345", saved.providerId)
        assertEquals(AuthProvider.KAKAO, saved.provider)
        assertEquals("u@kakao.com", saved.email)
        assertEquals("홍길동", saved.name)
        assertEquals("01-01", saved.birthDay)
        assertEquals(saved.id, principal.userId)
    }

    @Test
    fun `기존 사용자는 저장 없이 반환`() {
        val (svc, repo) = service()

        svc.processOAuth2User("kakao", rawKakao)
        val countAfterFirst = repo.all().size
        val principal = svc.processOAuth2User("kakao", rawKakao) as UserPrincipal

        assertEquals(countAfterFirst, repo.all().size)
        assertEquals(repo.all().first().id, principal.userId)
    }

    @Test
    fun `email 미동의 신규는 디폴트 이메일`() {
        val (svc, repo) = service()
        val raw = mapOf<String, Any>(
            "id" to 999L,
            "kakao_account" to mapOf("profile" to mapOf("nickname" to "x")),
        )

        svc.processOAuth2User("kakao", raw)

        assertEquals("999@kakao.local", repo.all().first().email)
    }
}
