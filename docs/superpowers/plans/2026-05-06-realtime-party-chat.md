# 실시간 파티 채팅 기능 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** REALTIME 파티 참여자(주최자 + 링크 참여자, 로그인 여부 무관)가 LIVE_OPEN 상태에서 실시간 채팅할 수 있도록 SSE 기반 채팅 기능을 구현한다.

**Architecture:** 라이브 입장 시 `participantToken`(UUID)을 발급해 비인증 사용자를 식별한다. 메시지 전송은 REST POST, 수신은 SSE로 처리한다. `SseEmitterRegistry` 빈이 emitter 생명주기를 관리하고, `SendChatMessageUseCase`와 `SubscribeChatUseCase`가 비즈니스 검증을 담당한다.

**Tech Stack:** Kotlin, Spring Boot 4, Spring WebMVC (`SseEmitter`), Spring Security (JWT + X-Participant-Token 커스텀 헤더), JPA/Hibernate, Mockito, MockMvc

---

## 파일 맵

### 생성

| 파일 | 역할 |
|------|------|
| `src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyRequest.kt` | 라이브 입장 요청 DTO |
| `src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyResponse.kt` | 라이브 입장 응답 DTO |
| `src/main/kotlin/com/team2/server/chat/dto/SendChatMessageRequest.kt` | 메시지 전송 요청 DTO |
| `src/main/kotlin/com/team2/server/chat/dto/ChatMessageResponse.kt` | 메시지 응답 DTO |
| `src/main/kotlin/com/team2/server/chat/service/SseEmitterRegistry.kt` | emitter 등록/브로드캐스트 |
| `src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt` | 라이브 입장, participantToken 발급 |
| `src/main/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCase.kt` | 메시지 전송 검증 + 저장 + 브로드캐스트 |
| `src/main/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCase.kt` | SSE 구독, 히스토리 전송 |
| `src/main/kotlin/com/team2/server/chat/controller/ChatApi.kt` | Swagger 인터페이스 |
| `src/main/kotlin/com/team2/server/chat/controller/ChatController.kt` | REST + SSE 엔드포인트 |
| `src/test/kotlin/com/team2/server/chat/service/SseEmitterRegistryTest.kt` | Registry 단위 테스트 |
| `src/test/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCaseTest.kt` | 입장 UseCase 단위 테스트 |
| `src/test/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCaseTest.kt` | 전송 UseCase 단위 테스트 |
| `src/test/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCaseTest.kt` | 구독 UseCase 단위 테스트 |
| `src/test/kotlin/com/team2/server/chat/controller/ChatControllerTest.kt` | 컨트롤러 통합 테스트 |

### 수정

| 파일 | 변경 내용 |
|------|----------|
| `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` | CHAT_NOT_SUPPORTED, CHAT_NOT_ACTIVE 추가 |
| `src/main/kotlin/com/team2/server/party/entity/RealtimeParticipantProfile.kt` | participantToken 필드 추가 |
| `src/main/kotlin/com/team2/server/chat/entity/ChatMessage.kt` | participant → profile 로 교체 |
| `src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt` | findAllByPartyIdOrderByCreatedAtAsc 추가 |
| `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt` | findByParticipantToken 추가 |
| `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` | 채팅 관련 엔드포인트 permitAll 추가 |

---

## Task 1: 기반 코드 수정

**Files:**
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`
- Modify: `src/main/kotlin/com/team2/server/party/entity/RealtimeParticipantProfile.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/entity/ChatMessage.kt`
- Modify: `src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt`
- Modify: `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`

- [ ] **Step 1: ErrorCode에 채팅 에러코드 추가**

`src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`의 마지막 항목 뒤에 추가:

```kotlin
CHAT_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "채팅을 지원하지 않는 파티입니다"),
CHAT_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "현재 채팅이 활성화된 시간이 아닙니다"),
```

- [ ] **Step 2: RealtimeParticipantProfile에 participantToken 추가**

`src/main/kotlin/com/team2/server/party/entity/RealtimeParticipantProfile.kt` 전체 교체:

```kotlin
package com.team2.server.party.entity

import com.team2.server.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "realtime_participant_profile",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_realtime_participant_profile_participant",
            columnNames = ["participant_id"],
        ),
        UniqueConstraint(
            name = "uk_realtime_participant_profile_token",
            columnNames = ["participant_token"],
        ),
    ],
)
class RealtimeParticipantProfile(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    var participant: Participant,
    @Column(name = "nickname", nullable = false, length = 20)
    var nickname: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id")
    var character: Character? = null,
    @Column(name = "participant_token", nullable = false, length = 36)
    val participantToken: String = UUID.randomUUID().toString(),
) : BaseEntity()
```

- [ ] **Step 3: ChatMessage 엔티티 수정 (participant → profile)**

`src/main/kotlin/com/team2/server/chat/entity/ChatMessage.kt` 전체 교체:

```kotlin
package com.team2.server.chat.entity

import com.team2.server.common.entity.BaseEntity
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.Party
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "chat_message")
class ChatMessage(
    @Column(nullable = false, length = 1000)
    var content: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "party_id", nullable = false)
    var party: Party,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    var profile: RealtimeParticipantProfile,
) : BaseEntity()
```

- [ ] **Step 4: ChatMessageRepository 메서드 추가**

`src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt` 전체 교체:

```kotlin
package com.team2.server.chat.repository

import com.team2.server.chat.entity.ChatMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)

    fun findAllByPartyIdOrderByCreatedAtAsc(partyId: Long): List<ChatMessage>
}
```

- [ ] **Step 5: RealtimeParticipantProfileRepository에 토큰 조회 추가**

`src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt` 전체 교체:

```kotlin
package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional

interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?

    fun findByParticipantToken(participantToken: String): RealtimeParticipantProfile?

    @Modifying
    @Transactional
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
}
```

- [ ] **Step 6: SecurityConfig에 채팅 엔드포인트 permitAll 추가**

`src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt`의 `authorizeHttpRequests` 블록에 아래 두 줄 추가 (기존 permitAll 라인들 바로 뒤):

```kotlin
auth.requestMatchers(HttpMethod.POST, "/api/v1/party-invites/*/realtime-participants").permitAll()
auth.requestMatchers(HttpMethod.POST, "/api/v1/parties/*/chat-messages").permitAll()
auth.requestMatchers(HttpMethod.GET, "/api/v1/parties/*/chat-messages/stream").permitAll()
```

- [ ] **Step 7: 빌드 확인**

```bash
./gradlew build -x test
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt \
        src/main/kotlin/com/team2/server/party/entity/RealtimeParticipantProfile.kt \
        src/main/kotlin/com/team2/server/chat/entity/ChatMessage.kt \
        src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt \
        src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt \
        src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt
git commit -m "feat: 채팅 기반 코드 수정 (에러코드, 엔티티, 레포지토리, 보안 설정)"
```

---

## Task 2: EnterRealtimePartyUseCase

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyRequest.kt`
- Create: `src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyResponse.kt`
- Create: `src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt`
- Create: `src/test/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCaseTest.kt`

- [ ] **Step 1: DTO 생성**

`src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyRequest.kt`:

```kotlin
package com.team2.server.chat.dto

data class EnterRealtimePartyRequest(
    val nickname: String,
    val characterId: Long,
)
```

`src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyResponse.kt`:

```kotlin
package com.team2.server.chat.dto

data class EnterRealtimePartyResponse(
    val participantToken: String,
)
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCaseTest.kt`:

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class EnterRealtimePartyUseCaseTest {

    @Mock lateinit var partyInviteRepository: PartyInviteRepository
    @Mock lateinit var participantRepository: ParticipantRepository
    @Mock lateinit var realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository
    @Mock lateinit var characterRepository: CharacterRepository
    @Mock lateinit var userRepository: UserRepository

    @InjectMocks
    lateinit var useCase: EnterRealtimePartyUseCase

    private val request = EnterRealtimePartyRequest(nickname = "토끼왕", characterId = 1L)

    @Test
    fun `존재하지 않는 초대 토큰이면 PARTY_NOT_FOUND`() {
        whenever(partyInviteRepository.findByToken("invalid")).thenReturn(null)

        val ex = assertThrows<BusinessException> { useCase.enter("invalid", userId = null, request) }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `만료된 초대링크면 INVITE_LINK_EXPIRED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().minusSeconds(1))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.INVITE_LINK_EXPIRED, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 캐릭터면 CHARACTER_NOT_FOUND`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.empty())

        val ex = assertThrows<BusinessException> { useCase.enter("tok", userId = null, request) }
        assertEquals(ErrorCode.CHARACTER_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `비로그인 사용자 첫 입장 - 익명 Participant + Profile 생성 후 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕", character = character)

        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.save(any())).thenReturn(participant)
        whenever(realtimeParticipantProfileRepository.findByParticipant(participant)).thenReturn(null)
        whenever(realtimeParticipantProfileRepository.save(any())).thenReturn(profile)

        val response = useCase.enter("tok", userId = null, request)

        assertNotNull(response.participantToken)
    }

    @Test
    fun `이미 프로필이 있는 사용자 재입장 - 기존 token 반환`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        val invite = PartyInvite(party = party, token = "tok", expiresAt = LocalDateTime.now().plusDays(7))
        val character = Character(name = "토끼")
        val participant = Participant(party = party)
        val existingProfile = RealtimeParticipantProfile(
            participant = participant,
            nickname = "기존닉네임",
            character = null,
            participantToken = "existing-uuid",
        )

        whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
        whenever(characterRepository.findById(1L)).thenReturn(java.util.Optional.of(character))
        whenever(participantRepository.save(any())).thenReturn(participant)
        whenever(realtimeParticipantProfileRepository.findByParticipant(participant)).thenReturn(existingProfile)

        val response = useCase.enter("tok", userId = null, request)

        assertEquals("existing-uuid", response.participantToken)
    }
}
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.EnterRealtimePartyUseCaseTest" 2>&1 | tail -20
```

Expected: FAILED (EnterRealtimePartyUseCase not found)

- [ ] **Step 4: EnterRealtimePartyUseCase 구현**

`src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt`:

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EnterRealtimePartyUseCase(
    private val partyInviteRepository: PartyInviteRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun enter(
        inviteToken: String,
        userId: Long?,
        request: EnterRealtimePartyRequest,
    ): EnterRealtimePartyResponse {
        val invite = partyInviteRepository.findByToken(inviteToken)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        val party = invite.party
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        if (!invite.expiresAt.isAfter(LocalDateTime.now())) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }

        val character = characterRepository.findByIdOrNull(request.characterId)
            ?: throw BusinessException(ErrorCode.CHARACTER_NOT_FOUND)

        val participant = findOrCreateParticipant(party.id, userId, party)
        val profile = realtimeParticipantProfileRepository.findByParticipant(participant)

        if (profile != null) {
            profile.nickname = request.nickname
            profile.character = character
            return EnterRealtimePartyResponse(participantToken = profile.participantToken)
        }

        val newProfile = realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(
                participant = participant,
                nickname = request.nickname,
                character = character,
            )
        )
        return EnterRealtimePartyResponse(participantToken = newProfile.participantToken)
    }

    private fun findOrCreateParticipant(
        partyId: Long,
        userId: Long?,
        party: com.team2.server.party.entity.Party,
    ): Participant {
        if (userId != null) {
            val existing = participantRepository.findByPartyIdAndUserId(partyId, userId)
            if (existing != null) return existing
            val user = userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
            return participantRepository.save(Participant(party = party, user = user))
        }
        return participantRepository.save(Participant(party = party))
    }
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.EnterRealtimePartyUseCaseTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyRequest.kt \
        src/main/kotlin/com/team2/server/chat/dto/EnterRealtimePartyResponse.kt \
        src/main/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCase.kt \
        src/test/kotlin/com/team2/server/chat/usecase/EnterRealtimePartyUseCaseTest.kt
git commit -m "feat: EnterRealtimePartyUseCase 구현 (라이브 입장 및 participantToken 발급)"
```

---

## Task 3: SseEmitterRegistry

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/service/SseEmitterRegistry.kt`
- Create: `src/test/kotlin/com/team2/server/chat/service/SseEmitterRegistryTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/chat/service/SseEmitterRegistryTest.kt`:

```kotlin
package com.team2.server.chat.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlin.test.assertEquals

class SseEmitterRegistryTest {

    private lateinit var registry: SseEmitterRegistry

    @BeforeEach
    fun setUp() {
        registry = SseEmitterRegistry()
    }

    @Test
    fun `등록된 emitter가 없으면 broadcast 시 아무 일도 없음`() {
        registry.broadcast(1L, SseEmitter.event().name("message").data("hello").build())
        // 예외 없이 통과하면 OK
    }

    @Test
    fun `subscribe 후 complete하면 registry에서 제거됨`() {
        val emitter = SseEmitter(1000L)
        registry.subscribe(1L, emitter)
        assertEquals(1, registry.count(1L))

        emitter.complete()
        assertEquals(0, registry.count(1L))
    }

    @Test
    fun `subscribe 후 timeout하면 registry에서 제거됨`() {
        val emitter = SseEmitter(1000L)
        registry.subscribe(1L, emitter)
        assertEquals(1, registry.count(1L))

        emitter.onTimeout { }
        emitter.completeWithError(RuntimeException("timeout"))
        assertEquals(0, registry.count(1L))
    }

    @Test
    fun `여러 emitter 등록 후 broadcast 호출 시 모두에게 전달`() {
        val results = mutableListOf<String>()
        val emitter1 = SseEmitter(5000L)
        val emitter2 = SseEmitter(5000L)

        registry.subscribe(1L, emitter1)
        registry.subscribe(1L, emitter2)
        assertEquals(2, registry.count(1L))
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.team2.server.chat.service.SseEmitterRegistryTest" 2>&1 | tail -20
```

Expected: FAILED (SseEmitterRegistry not found)

- [ ] **Step 3: SseEmitterRegistry 구현**

`src/main/kotlin/com/team2/server/chat/service/SseEmitterRegistry.kt`:

```kotlin
package com.team2.server.chat.service

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class SseEmitterRegistry {

    private val emitters = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(partyId: Long, emitter: SseEmitter) {
        emitters.getOrPut(partyId) { CopyOnWriteArrayList() }.add(emitter)

        val remove = Runnable { remove(partyId, emitter) }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove.run() }
    }

    fun broadcast(partyId: Long, event: SseEmitter.SseEventBuilder) {
        val list = emitters[partyId] ?: return
        val dead = mutableListOf<SseEmitter>()
        for (emitter in list) {
            try {
                emitter.send(event)
            } catch (e: Exception) {
                dead.add(emitter)
            }
        }
        list.removeAll(dead)
    }

    fun count(partyId: Long): Int = emitters[partyId]?.size ?: 0

    private fun remove(partyId: Long, emitter: SseEmitter) {
        emitters[partyId]?.remove(emitter)
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.team2.server.chat.service.SseEmitterRegistryTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/chat/service/SseEmitterRegistry.kt \
        src/test/kotlin/com/team2/server/chat/service/SseEmitterRegistryTest.kt
git commit -m "feat: SseEmitterRegistry 구현 (emitter 등록/브로드캐스트/자동 해제)"
```

---

## Task 4: SendChatMessageUseCase

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/dto/SendChatMessageRequest.kt`
- Create: `src/main/kotlin/com/team2/server/chat/dto/ChatMessageResponse.kt`
- Create: `src/main/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCase.kt`
- Create: `src/test/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCaseTest.kt`

- [ ] **Step 1: DTO 생성**

`src/main/kotlin/com/team2/server/chat/dto/SendChatMessageRequest.kt`:

```kotlin
package com.team2.server.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SendChatMessageRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val content: String,
)
```

`src/main/kotlin/com/team2/server/chat/dto/ChatMessageResponse.kt`:

```kotlin
package com.team2.server.chat.dto

import com.team2.server.chat.entity.ChatMessage
import java.time.LocalDateTime

data class ChatMessageResponse(
    val messageId: Long,
    val content: String,
    val senderNickname: String,
    val senderCharacterId: Long?,
    val sentAt: LocalDateTime,
) {
    companion object {
        fun from(message: ChatMessage): ChatMessageResponse =
            ChatMessageResponse(
                messageId = message.id,
                content = message.content,
                senderNickname = message.profile.nickname,
                senderCharacterId = message.profile.character?.id,
                sentAt = message.createdAt,
            )
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCaseTest.kt`:

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.entity.RealtimePartyStatus
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class SendChatMessageUseCaseTest {

    @Mock lateinit var partyRepository: PartyRepository
    @Mock lateinit var participantRepository: ParticipantRepository
    @Mock lateinit var profileRepository: RealtimeParticipantProfileRepository
    @Mock lateinit var chatMessageRepository: ChatMessageRepository
    @Mock lateinit var sseEmitterRegistry: SseEmitterRegistry

    @InjectMocks
    lateinit var useCase: SendChatMessageUseCase

    private val request = SendChatMessageRequest(content = "안녕하세요!")

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
        }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
        }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `LIVE_OPEN이 아니면 CHAT_NOT_ACTIVE`() {
        // startedAt을 미래로 설정 → ROLLING_PAPER_OPEN 상태
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().plusHours(1))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
        }
        assertEquals(ErrorCode.CHAT_NOT_ACTIVE, ex.errorCode)
    }

    @Test
    fun `JWT + 파티 미소속이면 PARTY_FORBIDDEN`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 99L)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = 99L, participantToken = null, request)
        }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `JWT + 프로필 없으면 CHARACTER_REQUIRED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(participant)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = 10L, participantToken = null, request)
        }
        assertEquals(ErrorCode.CHARACTER_REQUIRED, ex.errorCode)
    }

    @Test
    fun `participantToken + 다른 파티 프로필이면 PARTY_FORBIDDEN`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val otherParty = RealtimeParty(ownerId = 2L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = otherParty)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "닉", participantToken = "tok")
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileRepository.findByParticipantToken("tok")).thenReturn(profile)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)
        }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `JWT로 메시지 전송 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕")
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(participant)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        val response = useCase.send(partyId = 1L, userId = 10L, participantToken = null, request)

        assertEquals("안녕하세요!", response.content)
        assertEquals("토끼왕", response.senderNickname)
        verify(sseEmitterRegistry).broadcast(any(), any())
    }

    @Test
    fun `participantToken으로 메시지 전송 성공`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "손님", participantToken = "tok")
        val savedMessage = ChatMessage(content = "안녕하세요!", party = party, profile = profile)
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(profileRepository.findByParticipantToken("tok")).thenReturn(profile)
        whenever(chatMessageRepository.save(any())).thenReturn(savedMessage)

        val response = useCase.send(partyId = 1L, userId = null, participantToken = "tok", request)

        assertEquals("손님", response.senderNickname)
        verify(sseEmitterRegistry).broadcast(any(), any())
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.send(partyId = 1L, userId = null, participantToken = null, request)
        }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }
}
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.SendChatMessageUseCaseTest" 2>&1 | tail -20
```

Expected: FAILED

- [ ] **Step 4: SendChatMessageUseCase 구현**

`src/main/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCase.kt`:

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.entity.RealtimePartyStatus
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SendChatMessageUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    @Transactional
    fun send(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ChatMessageResponse {
        val party = partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }
        if ((party as RealtimeParty).status() != RealtimePartyStatus.LIVE_OPEN) {
            throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
        }

        val profile = resolveProfile(userId, participantToken, partyId, party)

        val message = chatMessageRepository.save(
            ChatMessage(content = request.content, party = party, profile = profile)
        )

        val response = ChatMessageResponse.from(message)
        sseEmitterRegistry.broadcast(
            partyId,
            SseEmitter.event().name("message").data(response).build()
        )
        return response
    }

    private fun resolveProfile(
        userId: Long?,
        participantToken: String?,
        partyId: Long,
        party: com.team2.server.party.entity.Party,
    ): RealtimeParticipantProfile {
        if (userId != null) {
            val participant = participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            return profileRepository.findByParticipant(participant)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        }

        if (participantToken != null) {
            val profile = profileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
            if (profile.participant.party.id != partyId) {
                throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            }
            return profile
        }

        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.SendChatMessageUseCaseTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 8 tests passed

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/chat/dto/SendChatMessageRequest.kt \
        src/main/kotlin/com/team2/server/chat/dto/ChatMessageResponse.kt \
        src/main/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCase.kt \
        src/test/kotlin/com/team2/server/chat/usecase/SendChatMessageUseCaseTest.kt
git commit -m "feat: SendChatMessageUseCase 구현 (메시지 전송 + SSE 브로드캐스트)"
```

---

## Task 5: SubscribeChatUseCase

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCase.kt`
- Create: `src/test/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCaseTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCaseTest.kt`:

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class SubscribeChatUseCaseTest {

    @Mock lateinit var partyRepository: PartyRepository
    @Mock lateinit var participantRepository: ParticipantRepository
    @Mock lateinit var profileRepository: RealtimeParticipantProfileRepository
    @Mock lateinit var chatMessageRepository: ChatMessageRepository
    @Mock lateinit var sseEmitterRegistry: SseEmitterRegistry

    @InjectMocks
    lateinit var useCase: SubscribeChatUseCase

    @Test
    fun `파티 없으면 PARTY_NOT_FOUND`() {
        whenever(partyRepository.findPartyById(1L)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = "tok")
        }
        assertEquals(ErrorCode.PARTY_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `PAPER_ONLY 파티면 CHAT_NOT_SUPPORTED`() {
        val party = PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = "tok")
        }
        assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, ex.errorCode)
    }

    @Test
    fun `JWT + 파티 미소속이면 PARTY_FORBIDDEN`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 99L)).thenReturn(null)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = 99L, participantToken = null)
        }
        assertEquals(ErrorCode.PARTY_FORBIDDEN, ex.errorCode)
    }

    @Test
    fun `userId도 없고 participantToken도 없으면 UNAUTHORIZED`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        whenever(partyRepository.findPartyById(1L)).thenReturn(party)

        val ex = assertThrows<BusinessException> {
            useCase.subscribe(partyId = 1L, userId = null, participantToken = null)
        }
        assertEquals(ErrorCode.UNAUTHORIZED, ex.errorCode)
    }

    @Test
    fun `구독 성공 - 히스토리 전송 후 emitter 등록`() {
        val party = RealtimeParty(ownerId = 1L, startedAt = LocalDateTime.now().minusMinutes(5))
        val participant = Participant(party = party)
        val profile = RealtimeParticipantProfile(participant = participant, nickname = "토끼왕")
        val msg = ChatMessage(content = "기존메시지", party = party, profile = profile)

        whenever(partyRepository.findPartyById(1L)).thenReturn(party)
        whenever(participantRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(participant)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(profile)
        whenever(chatMessageRepository.findAllByPartyIdOrderByCreatedAtAsc(1L)).thenReturn(listOf(msg))

        val emitter = useCase.subscribe(partyId = 1L, userId = 10L, participantToken = null)

        assertNotNull(emitter)
        verify(sseEmitterRegistry).subscribe(any(), any())
    }
}
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.SubscribeChatUseCaseTest" 2>&1 | tail -20
```

Expected: FAILED

- [ ] **Step 3: SubscribeChatUseCase 구현**

`src/main/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCase.kt`:

```kotlin
package com.team2.server.chat.usecase

import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.chat.service.SseEmitterRegistry
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Service
class SubscribeChatUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val profileRepository: RealtimeParticipantProfileRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val sseEmitterRegistry: SseEmitterRegistry,
) {
    fun subscribe(
        partyId: Long,
        userId: Long?,
        participantToken: String?,
    ): SseEmitter {
        val party = partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
        }

        resolveProfile(userId, participantToken, partyId, party)

        val emitter = SseEmitter(15 * 60 * 1000L)

        val history = chatMessageRepository.findAllByPartyIdOrderByCreatedAtAsc(partyId)
            .map { ChatMessageResponse.from(it) }

        try {
            emitter.send(SseEmitter.event().name("history").data(history).build())
        } catch (e: Exception) {
            emitter.completeWithError(e)
            return emitter
        }

        sseEmitterRegistry.subscribe(partyId, emitter)
        return emitter
    }

    private fun resolveProfile(
        userId: Long?,
        participantToken: String?,
        partyId: Long,
        party: com.team2.server.party.entity.Party,
    ): RealtimeParticipantProfile {
        if (userId != null) {
            val participant = participantRepository.findByPartyIdAndUserId(partyId, userId)
                ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            return profileRepository.findByParticipant(participant)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
        }

        if (participantToken != null) {
            val profile = profileRepository.findByParticipantToken(participantToken)
                ?: throw BusinessException(ErrorCode.CHARACTER_REQUIRED)
            if (profile.participant.party.id != partyId) {
                throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
            }
            return profile
        }

        throw BusinessException(ErrorCode.UNAUTHORIZED)
    }
}
```

- [ ] **Step 4: 테스트 실행 — 통과 확인**

```bash
./gradlew test --tests "com.team2.server.chat.usecase.SubscribeChatUseCaseTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 5 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCase.kt \
        src/test/kotlin/com/team2/server/chat/usecase/SubscribeChatUseCaseTest.kt
git commit -m "feat: SubscribeChatUseCase 구현 (SSE 구독 + 히스토리 전송)"
```

---

## Task 6: ChatController + ChatApi + 통합 테스트

**Files:**
- Create: `src/main/kotlin/com/team2/server/chat/controller/ChatApi.kt`
- Create: `src/main/kotlin/com/team2/server/chat/controller/ChatController.kt`
- Create: `src/test/kotlin/com/team2/server/chat/controller/ChatControllerTest.kt`

- [ ] **Step 1: 통합 테스트 작성**

`src/test/kotlin/com/team2/server/chat/controller/ChatControllerTest.kt`:

```kotlin
package com.team2.server.chat.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyInvite
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val chatMessageRepository: ChatMessageRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val characterRepository: CharacterRepository,
        private val userRepository: UserRepository,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)
        private val objectMapper = ObjectMapper()

        @BeforeEach
        fun setUp() {
            chatMessageRepository.deleteAll()
            profileRepository.deleteAll()
            participantRepository.deleteAll()
            partyInviteRepository.deleteAll()
            partyRepository.deleteAll()
            userRepository.deleteAll()
            characterRepository.deleteAll()
        }

        // ─── 라이브 입장 ───

        @Test
        fun `비로그인 사용자 라이브 입장 성공`() {
            val (party, invite) = savePartyWithInvite(startedAt = LocalDateTime.now().plusHours(1))
            val character = characterRepository.save(Character(name = "토끼"))

            mockMvc.post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname": "손님A", "characterId": ${character.id}}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.data.participantToken") { isString() }
            }

            assertEquals(1, profileRepository.findAll().size)
        }

        @Test
        fun `존재하지 않는 초대 토큰으로 입장 시 404`() {
            val character = characterRepository.save(Character(name = "토끼"))

            mockMvc.post("/api/v1/party-invites/invalid-token/realtime-participants") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname": "손님", "characterId": ${character.id}}"""
            }.andExpect {
                status { isNotFound() }
            }
        }

        @Test
        fun `PAPER_ONLY 파티 입장 시 400`() {
            val party = partyRepository.save(
                com.team2.server.party.entity.PaperOnlyParty(ownerId = 1L, startedAt = LocalDateTime.now())
            )
            val invite = partyInviteRepository.save(
                PartyInvite(party = party, token = "paper-tok", expiresAt = LocalDateTime.now().plusDays(7))
            )
            val character = characterRepository.save(Character(name = "토끼"))

            mockMvc.post("/api/v1/party-invites/${invite.token}/realtime-participants") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"nickname": "손님", "characterId": ${character.id}}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }

        // ─── 메시지 전송 ───

        @Test
        fun `JWT로 메시지 전송 성공`() {
            val user = saveUser("kakao-chat-1", "chat1@kakao.local")
            val jwtToken = tokenProvider.issue(user)
            val (party, _) = savePartyWithInvite(startedAt = LocalDateTime.now().minusMinutes(5))
            val participant = participantRepository.save(Participant(party = party, user = user))
            saveProfile(participant)

            mockMvc.post("/api/v1/parties/${party.id}/chat-messages") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $jwtToken")
                content = """{"content": "안녕하세요!"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.data.content") { value("안녕하세요!") }
                jsonPath("$.data.senderNickname") { value("토끼왕") }
            }

            assertEquals(1, chatMessageRepository.findAll().size)
        }

        @Test
        fun `participantToken으로 메시지 전송 성공`() {
            val (party, _) = savePartyWithInvite(startedAt = LocalDateTime.now().minusMinutes(5))
            val participant = participantRepository.save(Participant(party = party))
            val profile = profileRepository.save(
                RealtimeParticipantProfile(participant = participant, nickname = "손님")
            )

            mockMvc.post("/api/v1/parties/${party.id}/chat-messages") {
                contentType = MediaType.APPLICATION_JSON
                header("X-Participant-Token", profile.participantToken)
                content = """{"content": "반갑습니다!"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.data.senderNickname") { value("손님") }
            }
        }

        @Test
        fun `LIVE_OPEN이 아닌 파티에 메시지 전송 시 400`() {
            val user = saveUser("kakao-chat-2", "chat2@kakao.local")
            val jwtToken = tokenProvider.issue(user)
            // startedAt을 미래로 설정 → ROLLING_PAPER_OPEN
            val (party, _) = savePartyWithInvite(startedAt = LocalDateTime.now().plusHours(1))
            val participant = participantRepository.save(Participant(party = party, user = user))
            saveProfile(participant)

            mockMvc.post("/api/v1/parties/${party.id}/chat-messages") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $jwtToken")
                content = """{"content": "아직 시작 안함"}"""
            }.andExpect {
                status { isBadRequest() }
            }
        }

        @Test
        fun `인증 수단 없이 메시지 전송 시 401`() {
            val (party, _) = savePartyWithInvite(startedAt = LocalDateTime.now().minusMinutes(5))

            mockMvc.post("/api/v1/parties/${party.id}/chat-messages") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"content": "인증없음"}"""
            }.andExpect {
                status { isUnauthorized() }
            }
        }

        // ─── SSE 구독 ───

        @Test
        fun `JWT로 SSE 구독 성공`() {
            val user = saveUser("kakao-chat-3", "chat3@kakao.local")
            val jwtToken = tokenProvider.issue(user)
            val (party, _) = savePartyWithInvite(startedAt = LocalDateTime.now().minusMinutes(5))
            val participant = participantRepository.save(Participant(party = party, user = user))
            saveProfile(participant)

            mockMvc.get("/api/v1/parties/${party.id}/chat-messages/stream") {
                header("Authorization", "Bearer $jwtToken")
                accept = MediaType.TEXT_EVENT_STREAM
            }.andExpect {
                status { isOk() }
                header { string("Content-Type", org.hamcrest.Matchers.containsString("text/event-stream")) }
            }
        }

        @Test
        fun `participantToken으로 SSE 구독 성공`() {
            val (party, _) = savePartyWithInvite(startedAt = LocalDateTime.now().minusMinutes(5))
            val participant = participantRepository.save(Participant(party = party))
            val profile = profileRepository.save(
                RealtimeParticipantProfile(participant = participant, nickname = "손님")
            )

            mockMvc.get("/api/v1/parties/${party.id}/chat-messages/stream") {
                header("X-Participant-Token", profile.participantToken)
                accept = MediaType.TEXT_EVENT_STREAM
            }.andExpect {
                status { isOk() }
            }
        }

        // ─── 헬퍼 ───

        private fun saveUser(providerId: String, email: String): User =
            userRepository.save(
                User(name = "유저", birthDay = "01-01", provider = AuthProvider.KAKAO, providerId = providerId, email = email)
            )

        private fun savePartyWithInvite(startedAt: LocalDateTime): Pair<RealtimeParty, PartyInvite> {
            val party = partyRepository.save(RealtimeParty(ownerId = 1L, startedAt = startedAt)) as RealtimeParty
            val invite = partyInviteRepository.save(
                PartyInvite(party = party, token = "tok-${party.id}", expiresAt = LocalDateTime.now().plusDays(7))
            )
            return party to invite
        }

        private fun saveProfile(participant: Participant): RealtimeParticipantProfile =
            profileRepository.save(RealtimeParticipantProfile(participant = participant, nickname = "토끼왕"))
    }
```

- [ ] **Step 2: 테스트 실행 — 실패 확인**

```bash
./gradlew test --tests "com.team2.server.chat.controller.ChatControllerTest" 2>&1 | tail -20
```

Expected: FAILED (ChatController not found)

- [ ] **Step 3: ChatApi 인터페이스 생성**

`src/main/kotlin/com/team2/server/chat/controller/ChatApi.kt`:

```kotlin
package com.team2.server.chat.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "Chat", description = "실시간 채팅 API")
interface ChatApi {
    @Operation(summary = "라이브 입장 (닉네임 + 캐릭터 선택, participantToken 발급)")
    fun enterRealtimeParty(
        inviteToken: String,
        @Parameter(hidden = true) principal: UserPrincipal?,
        request: EnterRealtimePartyRequest,
    ): ApiResponse<EnterRealtimePartyResponse>

    @Operation(summary = "채팅 메시지 전송 (JWT 또는 X-Participant-Token)")
    fun sendMessage(
        partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        participantToken: String?,
        request: SendChatMessageRequest,
    ): ApiResponse<ChatMessageResponse>

    @Operation(summary = "채팅 SSE 구독 (JWT 또는 X-Participant-Token)")
    fun subscribe(
        partyId: Long,
        @Parameter(hidden = true) principal: UserPrincipal?,
        participantToken: String?,
    ): SseEmitter
}
```

- [ ] **Step 4: ChatController 구현**

`src/main/kotlin/com/team2/server/chat/controller/ChatController.kt`:

```kotlin
package com.team2.server.chat.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.chat.dto.ChatMessageResponse
import com.team2.server.chat.dto.EnterRealtimePartyRequest
import com.team2.server.chat.dto.EnterRealtimePartyResponse
import com.team2.server.chat.dto.SendChatMessageRequest
import com.team2.server.chat.usecase.EnterRealtimePartyUseCase
import com.team2.server.chat.usecase.SendChatMessageUseCase
import com.team2.server.chat.usecase.SubscribeChatUseCase
import com.team2.server.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
class ChatController(
    private val enterRealtimePartyUseCase: EnterRealtimePartyUseCase,
    private val sendChatMessageUseCase: SendChatMessageUseCase,
    private val subscribeChatUseCase: SubscribeChatUseCase,
) : ChatApi {

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/party-invites/{inviteToken}/realtime-participants")
    override fun enterRealtimeParty(
        @PathVariable inviteToken: String,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestBody @Valid request: EnterRealtimePartyRequest,
    ): ApiResponse<EnterRealtimePartyResponse> =
        ApiResponse.success(
            HttpStatus.CREATED,
            enterRealtimePartyUseCase.enter(inviteToken, principal?.userId, request),
        )

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/parties/{partyId}/chat-messages")
    override fun sendMessage(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
        @RequestBody @Valid request: SendChatMessageRequest,
    ): ApiResponse<ChatMessageResponse> =
        ApiResponse.success(
            HttpStatus.CREATED,
            sendChatMessageUseCase.send(partyId, principal?.userId, participantToken, request),
        )

    @GetMapping("/api/v1/parties/{partyId}/chat-messages/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    override fun subscribe(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestHeader(value = "X-Participant-Token", required = false) participantToken: String?,
    ): SseEmitter =
        subscribeChatUseCase.subscribe(partyId, principal?.userId, participantToken)
}
```

- [ ] **Step 5: 전체 테스트 실행 — 통과 확인**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, all tests passed

- [ ] **Step 6: 커밋**

```bash
git add src/main/kotlin/com/team2/server/chat/controller/ChatApi.kt \
        src/main/kotlin/com/team2/server/chat/controller/ChatController.kt \
        src/test/kotlin/com/team2/server/chat/controller/ChatControllerTest.kt
git commit -m "feat: ChatController 구현 (라이브 입장, 메시지 전송, SSE 구독)"
```

---

## 완료 기준

- [ ] `./gradlew test` 전체 통과
- [ ] `POST /api/v1/party-invites/{token}/realtime-participants` — participantToken 발급
- [ ] `POST /api/v1/parties/{id}/chat-messages` — JWT + X-Participant-Token 양방향 동작
- [ ] `GET /api/v1/parties/{id}/chat-messages/stream` — SSE history + 실시간 메시지
- [ ] LIVE_OPEN 외 상태에서 메시지 전송 차단
- [ ] PAPER_ONLY 파티 채팅 차단
