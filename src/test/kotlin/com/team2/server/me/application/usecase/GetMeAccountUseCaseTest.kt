package com.team2.server.me.application.usecase

import com.team2.server.auth.FakeUserRepository
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.me.config.SupportProperties
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class GetMeAccountUseCaseTest {
    private val supportProperties = SupportProperties(chatUrl = "https://open.kakao.com/o/test")

    @Test
    fun `사용자 정보와 1대1 문의 URL을 묶어 application Result 로 반환한다`() {
        val repository = FakeUserRepository()
        val user =
            repository.save(
                User(
                    name = "김이라",
                    birthDay = "03-15",
                    provider = AuthProvider.KAKAO,
                    providerId = "kakao-12345",
                    email = "ira@kakao.local",
                ),
            )
        user.createdAt = LocalDateTime.of(2026, 2, 23, 10, 0)
        val useCase = GetMeAccountUseCase(repository, supportProperties)

        val result = useCase.invoke(user.id)

        assertThat(result.nickname).isEqualTo("김이라")
        assertThat(result.provider).isEqualTo(AuthProvider.KAKAO)
        assertThat(result.connectedAt).isEqualTo(LocalDateTime.of(2026, 2, 23, 10, 0).toLocalDate())
        assertThat(result.supportChatUrl).isEqualTo("https://open.kakao.com/o/test")
    }

    @Test
    fun `userId 에 해당하는 사용자가 없으면 AUTH_USER_NOT_FOUND BusinessException`() {
        val repository = FakeUserRepository()
        val useCase = GetMeAccountUseCase(repository, supportProperties)

        assertThatThrownBy { useCase.invoke(999L) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AUTH_USER_NOT_FOUND)
    }
}
