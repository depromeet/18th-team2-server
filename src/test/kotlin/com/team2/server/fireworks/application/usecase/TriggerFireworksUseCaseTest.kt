package com.team2.server.fireworks.application.usecase

import com.team2.server.chat.application.port.PartySseEventPublisher
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.image.entity.ImageTargetType
import com.team2.server.common.image.persistence.ImageUrlReader
import com.team2.server.party.application.usecase.ResolveLiveOpenRealtimePartyUseCase
import com.team2.server.party.application.usecase.ResolveRealtimeParticipantProfileUseCase
import com.team2.server.party.domain.entity.Character
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class TriggerFireworksUseCaseTest {
    @Mock lateinit var resolveLiveOpenRealtimePartyUseCase: ResolveLiveOpenRealtimePartyUseCase

    @Mock lateinit var resolveRealtimeParticipantProfileUseCase: ResolveRealtimeParticipantProfileUseCase

    @Mock lateinit var imageUrlReader: ImageUrlReader

    @Mock lateinit var partySseEventPublisher: PartySseEventPublisher

    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-05-29T10:00:00Z"), ZoneId.of("Asia/Seoul"))

    private lateinit var useCase: TriggerFireworksUseCase

    @BeforeEach
    fun setUp() {
        useCase =
            TriggerFireworksUseCase(
                resolveLiveOpenRealtimePartyUseCase = resolveLiveOpenRealtimePartyUseCase,
                resolveRealtimeParticipantProfileUseCase = resolveRealtimeParticipantProfileUseCase,
                imageUrlReader = imageUrlReader,
                partySseEventPublisher = partySseEventPublisher,
                clock = fixedClock,
            )
    }

    private fun makeParty(): RealtimeParty = mock()

    private fun makeProfile(
        participantId: Long = 1L,
        nickname: String = "토끼왕",
        isCelebrant: Boolean = false,
        character: Character? = null,
    ): RealtimeParticipantProfile {
        val participant: Participant = mock()
        whenever(participant.id).thenReturn(participantId)
        whenever(participant.isCelebrant).thenReturn(isCelebrant)

        val profile: RealtimeParticipantProfile = mock()
        whenever(profile.participant).thenReturn(participant)
        whenever(profile.nickname).thenReturn(nickname)
        whenever(profile.character).thenReturn(character)
        return profile
    }

    @Test
    fun `파티가 LIVE_OPEN이 아니면 CHAT_NOT_ACTIVE를 던진다`() {
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(10L))
            .thenThrow(BusinessException(ErrorCode.CHAT_NOT_ACTIVE))

        val ex = assertThrows<BusinessException> { useCase.invoke(10L, null, "tok") }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    @Test
    fun `파티 참가자가 아니면 PARTY_FORBIDDEN을 던진다`() {
        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(10L)).thenReturn(makeParty())
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(10L, null, "tok"))
            .thenThrow(BusinessException(ErrorCode.PARTY_FORBIDDEN))

        val ex = assertThrows<BusinessException> { useCase.invoke(10L, null, "tok") }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `참가자가 폭죽을 트리거하면 SSE fireworks 이벤트가 broadcast된다`() {
        val character: Character = mock()
        whenever(character.id).thenReturn(2L)
        val profile = makeProfile(participantId = 5L, nickname = "토끼왕", isCelebrant = false, character = character)

        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(10L)).thenReturn(makeParty())
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(10L, null, "tok")).thenReturn(profile)
        whenever(imageUrlReader.findFirstImageUrlByTargetIds(eq(ImageTargetType.CHARACTER), eq(listOf(2L))))
            .thenReturn(mapOf(2L to "https://cdn.example.com/char2.png"))

        useCase.invoke(10L, null, "tok")

        verify(partySseEventPublisher).broadcastAfterCommit(eq(10L), any(), anyOrNull())
    }

    @Test
    fun `캐릭터 없는 참가자도 broadcast된다`() {
        val profile = makeProfile(participantId = 5L, nickname = "익명", character = null)

        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(10L)).thenReturn(makeParty())
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(10L, null, "tok")).thenReturn(profile)

        useCase.invoke(10L, null, "tok")

        verify(partySseEventPublisher).broadcastAfterCommit(eq(10L), any(), anyOrNull())
    }

    @Test
    fun `주최자(CELEBRANT)가 트리거해도 broadcast된다`() {
        val profile = makeProfile(participantId = 1L, nickname = "주최자", isCelebrant = true)

        whenever(resolveLiveOpenRealtimePartyUseCase.invoke(10L)).thenReturn(makeParty())
        whenever(resolveRealtimeParticipantProfileUseCase.invoke(10L, null, "tok")).thenReturn(profile)

        useCase.invoke(10L, null, "tok")

        verify(partySseEventPublisher).broadcastAfterCommit(eq(10L), any(), anyOrNull())
    }
}
