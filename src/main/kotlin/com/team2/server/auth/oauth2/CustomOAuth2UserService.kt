package com.team2.server.auth.oauth2

import com.team2.server.auth.oauth2.attributes.OAuth2AttributesFactory
import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val DEFAULT_BIRTH_DAY = "01-01"

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
) : DefaultOAuth2UserService() {
    @Transactional
    override fun loadUser(req: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(req)
        return processOAuth2User(req.clientRegistration.registrationId, oauth2User.attributes)
    }

    /**
     * 카카오 API 호출 결과(attributes)를 받아 사용자를 upsert 하고 UserPrincipal 을 만든다.
     * super.loadUser() 를 우회하여 단위 테스트가 가능하도록 분리.
     */
    fun processOAuth2User(
        registrationId: String,
        attributes: Map<String, Any>,
    ): OAuth2User {
        val attrs = OAuth2AttributesFactory.of(registrationId, attributes)

        val user =
            userRepository.findByProviderAndProviderId(attrs.provider, attrs.providerId)
                ?: userRepository.save(
                    User(
                        name = attrs.nickname,
                        birthDay = DEFAULT_BIRTH_DAY,
                        provider = attrs.provider,
                        providerId = attrs.providerId,
                        email = attrs.email,
                    ),
                )

        return UserPrincipal.from(user, attributes)
    }
}
