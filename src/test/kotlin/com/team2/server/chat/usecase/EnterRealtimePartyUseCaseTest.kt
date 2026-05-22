package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.CharacterService
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.PartyInviteService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PartyInvite
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterRealtimePartyUseCaseTest {
    @Mock lateinit var partyInviteService: PartyInviteService

    @Mock lateinit var participantService: ParticipantService

    @Mock lateinit var realtimeParticipantProfileService: RealtimeParticipantProfileService

    @Mock lateinit var characterService: CharacterService

    @Mock lateinit var userRepository: UserRepository

    lateinit var useCase: EnterRealtimePartyUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)

    @BeforeEach
    fun setUp() {
        useCase =
            EnterRealtimePartyUseCase(
                partyInviteService = partyInviteService,
                participantService = participantService,
                realtimeParticipantProfileService = realtimeParticipantProfileService,
                characterService = characterService,
                userRepository = userRepository,
                clock = clock,
            )
    }

    @Test
    fun `존재하지 않는 초대 토큰이면 PARTY_NOT_FOUND`() {
        whenever(partyInviteService.findUsableInvite(any(), any()))
            .thenThrow(BusinessException(ErrorCode.PARTY_NOT_FOUND))

        val ex = assertThrows<BusinessException> { useCase.enter("invalid", userId = null, request) }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = now)
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `입장 가능 시간 이전이면 CHAT_NOT_ACTIVE`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    @Test
    fun `만료된 초대링크면 INVITE_LINK_EXPIRED`() {
        whenever(partyInviteService.findUsableInvite(any(), any()))
            .thenThrow(BusinessException(ErrorCode.INVITE_LINK_EXPIRED))

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 캐릭터면 CHARACTER_NOT_FOUND`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(characterService.requireCharacter(1L)).thenThrow(BusinessException(ErrorCode.CHARACTER_NOT_FOUND))

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `비로그인 사용자 첫 입장 - 익명 Participant + Profile 생성 후 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(participantService.joinAnonymousOrMember(party, null)).thenReturn(participant)
        whenever(
            realtimeParticipantProfileService.upsert(
                participant = participant,
                nickname = "토끼왕",
                character = character,
                isHostNicknameLocked = false,
            ),
        ).thenReturn(profile)

        val result = useCase.enter("tok", userId = null, request)

        assertNotNull(result.participantToken)
    }

    @Test
    fun `주최자 첫 채팅 입장은 닉네임 잠금 옵션을 적용한다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party, isCelebrant = true)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "주최자", character = character)
        val user = user()

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        whenever(participantService.joinAnonymousOrMember(party, user)).thenReturn(participant)
        whenever(
            realtimeParticipantProfileService.upsert(
                participant = participant,
                nickname = "토끼왕",
                character = character,
                isHostNicknameLocked = true,
            ),
        ).thenReturn(profile)

        useCase.enter("tok", userId = 1L, request)

        verify(realtimeParticipantProfileService).upsert(
            participant = participant,
            nickname = "토끼왕",
            character = character,
            isHostNicknameLocked = true,
        )
    }

    @Test
    fun `이미 프로필이 있는 사용자 재입장 - 기존 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val existingProfile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "기존닉네임",
                character = null,
                participantToken = "existing-uuid",
            )

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(participantService.joinAnonymousOrMember(party, null)).thenReturn(participant)
        whenever(
            realtimeParticipantProfileService.upsert(
                participant = participant,
                nickname = "토끼왕",
                character = character,
                isHostNicknameLocked = false,
            ),
        ).thenReturn(existingProfile)

        val result = useCase.enter("tok", userId = null, request)

        assertEquals("existing-uuid", result.participantToken)
    }

    @Test
    fun `회원도 participantToken이 있으면 LIVE_ENDING에서 재입장할 수 있다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(10).minusSeconds(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val existingProfile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "기존닉네임",
                character = null,
                participantToken = "existing-uuid",
            )
        val reenterRequest =
            EnterRealtimePartyRequest(
                nickname = "새닉네임",
                characterId = 1L,
                participantToken = existingProfile.participantToken,
            )

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(
            realtimeParticipantProfileService.requireByParticipantToken(
                participantToken = existingProfile.participantToken,
                partyId = party.id,
            ),
        ).thenReturn(existingProfile)

        val result = useCase.enter("tok", userId = 99L, reenterRequest)

        assertEquals("existing-uuid", result.participantToken)
        assertEquals("새닉네임", existingProfile.nickname)
        assertEquals(character, existingProfile.character)
    }

    @Test
    fun `주최자는 participantToken 재입장으로 닉네임을 변경할 수 없다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = now.plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party, isCelebrant = true)
        val existingProfile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "기존닉네임",
                character = null,
                participantToken = "existing-uuid",
            )
        val reenterRequest =
            EnterRealtimePartyRequest(
                nickname = "새닉네임",
                characterId = 1L,
                participantToken = existingProfile.participantToken,
            )

        whenever(partyInviteService.findUsableInvite(any(), any())).thenReturn(invite)
        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(
            realtimeParticipantProfileService.requireByParticipantToken(
                participantToken = existingProfile.participantToken,
                partyId = party.id,
            ),
        ).thenReturn(existingProfile)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = 1L, reenterRequest) }

        assertEquals(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE, ex.errorCode)
        assertEquals("기존닉네임", existingProfile.nickname)
    }

    private fun user(): User =
        User(
            name = "회원",
            birthDay = "01-01",
            provider = AuthProvider.KAKAO,
            providerId = "member-1",
            email = "member@example.com",
        )
}
