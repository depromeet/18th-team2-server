package com.team2.server.chat.infrastructure.party

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.service.CharacterService
import com.team2.server.party.application.service.ParticipantService
import com.team2.server.party.application.service.RealtimeParticipantProfileService
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class PartyRealtimePartyEntryProfileAdapterTest {
    @Mock lateinit var participantService: ParticipantService

    @Mock lateinit var realtimeParticipantProfileService: RealtimeParticipantProfileService

    @Mock lateinit var characterService: CharacterService

    private val adapter: PartyRealtimePartyEntryProfileAdapter by lazy {
        PartyRealtimePartyEntryProfileAdapter(
            participantService = participantService,
            realtimeParticipantProfileService = realtimeParticipantProfileService,
            characterService = characterService,
        )
    }

    private val now = LocalDateTime.of(2026, 5, 23, 10, 0)
    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    @Test
    fun `첫 입장은 participant와 profile을 생성하고 entry result로 변환한다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val character = Character(name = "토끼")
        val participant = Participant(party = party, user = user(), isCelebrant = true)
        val profile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "토끼왕",
                character = character,
                participantToken = "participant-token",
            )

        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(participantService.resolveUser(1L)).thenReturn(participant.user)
        whenever(participantService.joinAnonymousOrMember(party, participant.user)).thenReturn(participant)
        whenever(
            realtimeParticipantProfileService.upsert(
                participant = participant,
                nickname = "토끼왕",
                character = character,
                isHostNicknameLocked = true,
            ),
        ).thenReturn(profile)

        val result = adapter.resolve(party = party, userId = 1L, request = request, now = now)

        assertEquals("participant-token", result.participantToken)
        assertEquals(true, result.isCelebrant)
        assertEquals("토끼왕", result.nickname)
        assertEquals(character.id, result.characterId)
    }

    @Test
    fun `participantToken 재입장은 LIVE_ENDING 상태에서 CHAT_NOT_ACTIVE`() {
        val startedAt = now.minusMinutes(10).minusSeconds(1)
        val party = RealtimeParty(ownerId = 1L, startedAt = startedAt).apply { liveStartedAt = startedAt }
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val profile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "기존닉네임",
                character = null,
                participantToken = "existing-token",
            )
        val reenterRequest =
            EnterRealtimePartyRequest(
                nickname = "새닉네임",
                characterId = 1L,
                participantToken = "existing-token",
            )

        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(
            realtimeParticipantProfileService.requireForReentryByParticipantToken(
                participantToken = "existing-token",
                partyId = party.id,
            ),
        ).thenReturn(profile)

        val ex =
            assertThrows<BusinessException> {
                adapter.resolve(party = party, userId = 99L, request = reenterRequest, now = now)
            }

        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
        assertEquals("기존닉네임", profile.nickname)
        assertEquals(null, profile.character)
    }

    @Test
    fun `주최자는 participantToken 재입장으로 닉네임을 변경할 수 없다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val character = Character(name = "토끼")
        val participant = Participant(party = party, isCelebrant = true)
        val profile =
            RealtimeParticipantProfile(
                participant = participant,
                nickname = "기존닉네임",
                character = null,
                participantToken = "existing-token",
            )
        val reenterRequest =
            EnterRealtimePartyRequest(
                nickname = "새닉네임",
                characterId = 1L,
                participantToken = "existing-token",
            )

        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(
            realtimeParticipantProfileService.requireForReentryByParticipantToken(
                participantToken = "existing-token",
                partyId = party.id,
            ),
        ).thenReturn(profile)

        val ex =
            assertThrows<BusinessException> {
                adapter.resolve(party = party, userId = 1L, request = reenterRequest, now = now)
            }

        assertEquals(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE, ex.errorCode)
        assertEquals("기존닉네임", profile.nickname)
    }

    @Test
    fun `첫 입장은 주최자 여부로 닉네임 잠금 옵션을 전달한다`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = now.minusMinutes(1))
        val character = Character(name = "토끼")
        val participant = Participant(party = party, isCelebrant = false)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)

        whenever(characterService.requireCharacter(1L)).thenReturn(character)
        whenever(participantService.resolveUser(null)).thenReturn(null)
        whenever(participantService.joinAnonymousOrMember(party, null)).thenReturn(participant)
        whenever(
            realtimeParticipantProfileService.upsert(
                participant = participant,
                nickname = "토끼왕",
                character = character,
                isHostNicknameLocked = false,
            ),
        ).thenReturn(profile)

        adapter.resolve(party = party, userId = null, request = request, now = now)

        verify(realtimeParticipantProfileService).upsert(
            participant = participant,
            nickname = "토끼왕",
            character = character,
            isHostNicknameLocked = false,
        )
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
