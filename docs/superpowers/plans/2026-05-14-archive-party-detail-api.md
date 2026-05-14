# Archive Party Detail API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 보관함 화면의 파티 상세 + 내가 남긴 롤페 모달 데이터를 하나의 API(`GET /api/v1/archive/party/{partyId}`)로 제공.

**Architecture:** `party/` feature 안에 4-레이어 패키지(`api/`, `application/usecase/`)로 신규 추가. UseCase가 `Party`, `Participant`, `RealtimeParticipantProfile`, `RollingPaper`, `ChatMessage` 데이터를 모아 평탄(flat) 응답 DTO로 조립한다. 기존 `controller/`, `service/`, `usecase/`, `dto/`, `repository/` 폴더는 건드리지 않고 기존 Repository에 메서드만 추가한다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.x, Spring Data JPA (Hibernate), Spring Security (JWT), JUnit 5 + `MockMvc` Kotlin DSL, `@DataJpaTest`.

**Related spec:** [`docs/superpowers/specs/2026-05-14-archive-party-detail-design.md`](../specs/2026-05-14-archive-party-detail-design.md)

---

## 사전 컨텍스트

이 plan은 develop 브랜치에서 시작한다. develop 기준 코드베이스 사실:

- `ParticipantRepository.findByPartyIdAndUserId(partyId: Long, userId: Long): Participant?` 는 이미 존재한다. 재사용한다.
- `RollingPaperRepository.countByParty(party: Party): Long` 가 이미 있다. 그대로 사용한다.
- `RealtimeParticipantProfileRepository`, `ChatMessageRepository`에는 이번 plan이 요구하는 메서드가 없다. 새로 추가한다.
- 테스트 베이스: `@SpringBootTest + @AutoConfigureMockMvc`(컨트롤러), `@DataJpaTest`(레포지토리). `DatabaseCleanup`은 `com.team2.server.common.DatabaseCleanup`.
- 인증 토큰: `JwtTokenProvider(jwtProperties).issue(user)`로 발급, 헤더 `Authorization: Bearer <token>`.
- `ApiResponse.success(data)` 로 응답을 감싸고, controller 메서드 반환 타입은 `ApiResponse<…>`.
- ErrorCode: `PARTY_NOT_FOUND`, `PARTY_FORBIDDEN`, `AUTH_INVALID_TOKEN` 모두 존재한다.
- Swagger 401 묶음 annotation `@com.team2.server.common.swagger.AuthErrorResponses` 가 있다.

커밋 컨벤션은 한국어 `<type>: 설명` (예: `feat: 보관함 파티 상세 API 추가`). `--no-verify` 금지. `develop` 직접 푸시 금지.

---

## File Structure

```
src/main/kotlin/com/team2/server/
├── party/
│   ├── api/                                            # 신규 디렉토리
│   │   ├── ArchivePartyDetailApi.kt                    # Swagger interface
│   │   ├── ArchivePartyDetailController.kt             # GET /api/v1/archive/party/{partyId}
│   │   └── dto/
│   │       ├── ArchivePartyDetailResponse.kt           # 통합 응답 + ArchiveRole enum
│   │       ├── ArchiveParticipantResponse.kt
│   │       └── ArchiveChatMessageResponse.kt
│   ├── application/
│   │   └── usecase/
│   │       └── GetArchivedPartyDetailUseCase.kt        # 흐름 + DTO 변환
│   └── repository/                                     # 기존 — 메서드만 추가
│       ├── RealtimeParticipantProfileRepository.kt     # 메서드 2개 추가
│       └── (ParticipantRepository.kt — 변경 없음)
├── rollingpaper/repository/
│   └── RollingPaperRepository.kt                       # 메서드 1개 추가
└── chat/repository/
    └── ChatMessageRepository.kt                        # 메서드 2개 추가

src/test/kotlin/com/team2/server/
├── party/
│   ├── api/
│   │   └── ArchivePartyDetailControllerTest.kt         # 통합 시나리오 전체
│   └── repository/
│       └── RealtimeParticipantProfileRepositoryTest.kt # 신규 메서드 단위
├── rollingpaper/repository/
│   └── RollingPaperRepositoryTest.kt                   # 기존이면 추가, 없으면 신규
└── chat/repository/
    └── ChatMessageRepositoryTest.kt                    # 신규
```

---

## Task 순서 개요

- Task 1: `RealtimeParticipantProfileRepository`에 메서드 2개 추가 (TDD)
- Task 2: `RollingPaperRepository.findByWriter` 추가 (TDD)
- Task 3: `ChatMessageRepository`에 메서드 2개 추가 (TDD)
- Task 4: 응답 DTO 추가 (data class만, 테스트 없음)
- Task 5: `GetArchivedPartyDetailUseCase` 스켈레톤
- Task 6: `ArchivePartyDetailApi` Swagger interface + `ArchivePartyDetailController`
- Task 7: 통합 테스트 작성 — 작성 → 실패 확인 → UseCase 완성 → 통과
- Task 8: 인증/권한 케이스 통합 테스트 추가
- Task 9: `./gradlew test` 전체 통과 확인 + 커밋 정리

각 Task는 한 커밋 단위다. 마지막 Task 9에서 PR을 만들지 여부는 사용자에게 묻는다.

---

## Task 1: `RealtimeParticipantProfileRepository` 메서드 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt`
- Create or modify: `src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt`

### Step 1.1: 테스트 파일 확인

- [ ] **확인:** 다음 명령으로 기존 테스트 파일 존재 여부 확인.

```bash
ls src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt 2>/dev/null || echo NOT_FOUND
```

존재하지 않으면 새로 만든다.

### Step 1.2: 실패하는 테스트 작성

- [ ] **Step 1.2.1:** `RealtimeParticipantProfileRepositoryTest.kt`에 다음 테스트 추가 (파일이 없으면 새로 생성).

```kotlin
package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
class RealtimeParticipantProfileRepositoryTest
    @Autowired
    constructor(
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `findAllByPartyIdOrderByIdAsc - 특정 파티의 프로필 전체를 id ASC로 반환`() {
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "테스트",
                        celebrantNickname = "홍",
                        purpose = PartyPurpose.BIRTHDAY,
                        startedAt = LocalDateTime.now(),
                    ),
                )
            val user1 = userRepository.save(User(email = "a@a", provider = AuthProvider.KAKAO, providerId = "1"))
            val user2 = userRepository.save(User(email = "b@b", provider = AuthProvider.KAKAO, providerId = "2"))
            val p1 = participantRepository.save(Participant(party = party, user = user1))
            val p2 = participantRepository.save(Participant(party = party, user = user2))
            profileRepository.save(RealtimeParticipantProfile(participant = p1, nickname = "해파리1"))
            profileRepository.save(RealtimeParticipantProfile(participant = p2, nickname = "해파리2"))

            val result = profileRepository.findAllByPartyIdOrderByIdAsc(party.id)

            assertEquals(2, result.size)
            assertEquals("해파리1", result[0].nickname)
            assertEquals("해파리2", result[1].nickname)
        }

        @Test
        fun `findAllByPartyIdOrderByIdAsc - 다른 파티 프로필은 제외`() {
            val party1 =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p1", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val party2 =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p2", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user = userRepository.save(User(email = "x@x", provider = AuthProvider.KAKAO, providerId = "x"))
            val pA = participantRepository.save(Participant(party = party1, user = user))
            val pB = participantRepository.save(Participant(party = party2, user = user))
            profileRepository.save(RealtimeParticipantProfile(participant = pA, nickname = "A"))
            profileRepository.save(RealtimeParticipantProfile(participant = pB, nickname = "B"))

            val result = profileRepository.findAllByPartyIdOrderByIdAsc(party1.id)

            assertEquals(listOf("A"), result.map { it.nickname })
        }

        @Test
        fun `findAllByParticipantIdIn - 주어진 participantId 들의 프로필을 반환`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user1 = userRepository.save(User(email = "1@x", provider = AuthProvider.KAKAO, providerId = "1"))
            val user2 = userRepository.save(User(email = "2@x", provider = AuthProvider.KAKAO, providerId = "2"))
            val p1 = participantRepository.save(Participant(party = party, user = user1))
            val p2 = participantRepository.save(Participant(party = party, user = user2))
            profileRepository.save(RealtimeParticipantProfile(participant = p1, nickname = "해A"))
            profileRepository.save(RealtimeParticipantProfile(participant = p2, nickname = "해B"))

            val result = profileRepository.findAllByParticipantIdIn(listOf(p1.id, p2.id))

            assertEquals(2, result.size)
            assertTrue(result.any { it.nickname == "해A" })
            assertTrue(result.any { it.nickname == "해B" })
        }

        @Test
        fun `findAllByParticipantIdIn - 빈 컬렉션이면 빈 리스트`() {
            val result = profileRepository.findAllByParticipantIdIn(emptyList())
            assertTrue(result.isEmpty())
        }
    }
```

### Step 1.3: 컴파일 실패 확인

- [ ] **실행:**

```bash
./gradlew compileTestKotlin
```

**Expected:** Kotlin compile error — `Unresolved reference: findAllByPartyIdOrderByIdAsc` 또는 `findAllByParticipantIdIn`.

### Step 1.4: 메서드 구현

- [ ] **Edit** `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt`:

```kotlin
package com.team2.server.party.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface RealtimeParticipantProfileRepository : JpaRepository<RealtimeParticipantProfile, Long> {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile?

    @Query(
        """
        SELECT profile
        FROM RealtimeParticipantProfile profile
        JOIN FETCH profile.participant participant
        WHERE participant.party.id = :partyId
        ORDER BY profile.id ASC
        """,
    )
    fun findAllByPartyIdOrderByIdAsc(partyId: Long): List<RealtimeParticipantProfile>

    fun findAllByParticipantIdIn(participantIds: Collection<Long>): List<RealtimeParticipantProfile>

    @Modifying
    @Transactional
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
}
```

### Step 1.5: 테스트 통과 확인

- [ ] **실행:**

```bash
./gradlew test --tests com.team2.server.party.repository.RealtimeParticipantProfileRepositoryTest
```

**Expected:** 4 tests passed.

### Step 1.6: 커밋

- [ ] **실행:**

```bash
git add \
  src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt \
  src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt
git commit -m "feat: RealtimeParticipantProfile 조회 메서드 추가"
```

---

## Task 2: `RollingPaperRepository.findByWriter` 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepository.kt`
- Create or modify: `src/test/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepositoryTest.kt`

### Step 2.1: 실패하는 테스트 작성

- [ ] **Step 2.1.1:** 다음 파일 추가 (없으면 새로 만들고, 있으면 메서드만 추가):

```kotlin
package com.team2.server.rollingpaper.repository

import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.PartyPurpose
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DataJpaTest
class RollingPaperRepositoryTest
    @Autowired
    constructor(
        private val rollingPaperRepository: RollingPaperRepository,
        private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `findByWriter - 작성자 participant 기준 단건 조회`() {
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = 1L,
                        name = "p",
                        celebrantNickname = "홍",
                        purpose = PartyPurpose.BIRTHDAY,
                        startedAt = LocalDateTime.now(),
                    ),
                )
            val user = userRepository.save(User(email = "a@a", provider = AuthProvider.KAKAO, providerId = "1"))
            val participant = participantRepository.save(Participant(party = party, user = user))
            val wrapper = rollingPaperWrapperRepository.save(RollingPaperWrapper(name = "Topping_Candle"))
            rollingPaperRepository.save(
                RollingPaper(
                    wrapper = wrapper,
                    writer = participant,
                    party = party,
                    writerNickname = "해파리",
                    content = "축하해",
                ),
            )

            val result = rollingPaperRepository.findByWriter(participant)

            assertEquals("축하해", result?.content)
            assertEquals("해파리", result?.writerNickname)
        }

        @Test
        fun `findByWriter - 작성 안 한 participant는 null`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user = userRepository.save(User(email = "a@a", provider = AuthProvider.KAKAO, providerId = "1"))
            val participant = participantRepository.save(Participant(party = party, user = user))

            val result = rollingPaperRepository.findByWriter(participant)

            assertNull(result)
        }
    }
```

### Step 2.2: 컴파일 실패 확인

- [ ] **실행:**

```bash
./gradlew compileTestKotlin
```

**Expected:** `Unresolved reference: findByWriter`.

### Step 2.3: 메서드 추가

- [ ] **Edit** `src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepository.kt` — 기존 인터페이스 본문 안 `countByParty` 라인 바로 다음에 추가:

```kotlin
    fun findByWriter(writer: com.team2.server.party.entity.Participant): RollingPaper?
```

import 정리 (파일 상단 import 블록에 추가):

```kotlin
import com.team2.server.party.entity.Participant
```

그리고 메서드 선언을 `fun findByWriter(writer: Participant): RollingPaper?` 로 단순화한다.

### Step 2.4: 테스트 통과 확인

- [ ] **실행:**

```bash
./gradlew test --tests com.team2.server.rollingpaper.repository.RollingPaperRepositoryTest
```

**Expected:** 2 tests passed.

### Step 2.5: 커밋

- [ ] **실행:**

```bash
git add \
  src/main/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepository.kt \
  src/test/kotlin/com/team2/server/rollingpaper/repository/RollingPaperRepositoryTest.kt
git commit -m "feat: 작성자 participant 기준 롤페 단건 조회 메서드 추가"
```

---

## Task 3: `ChatMessageRepository` 메서드 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt`
- Create: `src/test/kotlin/com/team2/server/chat/repository/ChatMessageRepositoryTest.kt`

### Step 3.1: 실패하는 테스트 작성

- [ ] **Create** `src/test/kotlin/com/team2/server/chat/repository/ChatMessageRepositoryTest.kt`:

```kotlin
package com.team2.server.chat.repository

import com.team2.server.chat.entity.ChatMessage
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
class ChatMessageRepositoryTest
    @Autowired
    constructor(
        private val chatMessageRepository: ChatMessageRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `countByPartyId - 해당 파티의 메시지 수 반환`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user = userRepository.save(User(email = "a@a", provider = AuthProvider.KAKAO, providerId = "1"))
            val participant = participantRepository.save(Participant(party = party, user = user))
            repeat(3) { idx ->
                chatMessageRepository.save(
                    ChatMessage(content = "m$idx", party = party, participant = participant),
                )
            }

            val count = chatMessageRepository.countByPartyId(party.id)

            assertEquals(3L, count)
        }

        @Test
        fun `findRecentByPartyId - createdAt DESC, id DESC 정렬로 최근 N개 반환`() {
            val party =
                partyRepository.save(
                    RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
                )
            val user = userRepository.save(User(email = "a@a", provider = AuthProvider.KAKAO, providerId = "1"))
            val participant = participantRepository.save(Participant(party = party, user = user))
            (1..5).forEach { idx ->
                chatMessageRepository.save(
                    ChatMessage(content = "m$idx", party = party, participant = participant),
                )
            }

            val recent = chatMessageRepository.findRecentByPartyId(party.id, PageRequest.of(0, 3))

            assertEquals(3, recent.size)
            assertEquals("m5", recent[0].content)
            assertEquals("m4", recent[1].content)
            assertEquals("m3", recent[2].content)
        }
    }
```

### Step 3.2: 컴파일 실패 확인

- [ ] **실행:** `./gradlew compileTestKotlin` — `Unresolved reference: countByPartyId` 또는 `findRecentByPartyId`.

### Step 3.3: 메서드 추가

- [ ] **Edit** `src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt`:

```kotlin
package com.team2.server.chat.repository

import com.team2.server.chat.entity.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun countByPartyId(partyId: Long): Long

    @Query(
        """
        SELECT cm
        FROM ChatMessage cm
        JOIN FETCH cm.participant
        WHERE cm.party.id = :partyId
        ORDER BY cm.createdAt DESC, cm.id DESC
        """,
    )
    fun findRecentByPartyId(
        partyId: Long,
        pageable: Pageable,
    ): List<ChatMessage>

    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)
}
```

### Step 3.4: 테스트 통과 확인

- [ ] **실행:**

```bash
./gradlew test --tests com.team2.server.chat.repository.ChatMessageRepositoryTest
```

**Expected:** 2 tests passed.

### Step 3.5: 커밋

- [ ] **실행:**

```bash
git add \
  src/main/kotlin/com/team2/server/chat/repository/ChatMessageRepository.kt \
  src/test/kotlin/com/team2/server/chat/repository/ChatMessageRepositoryTest.kt
git commit -m "feat: 채팅 메시지 카운트와 최근 메시지 조회 메서드 추가"
```

---

## Task 4: 응답 DTO 추가

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/dto/ArchivePartyDetailResponse.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/dto/ArchiveParticipantResponse.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/dto/ArchiveChatMessageResponse.kt`

### Step 4.1: `ArchiveParticipantResponse.kt` 작성

- [ ] **Create:**

```kotlin
package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "보관함 파티 상세 - 참가자 항목")
data class ArchiveParticipantResponse(
    @Schema(description = "참가자 닉네임 (RealtimeParticipantProfile.nickname)")
    val nickname: String,
)
```

### Step 4.2: `ArchiveChatMessageResponse.kt` 작성

- [ ] **Create:**

```kotlin
package com.team2.server.party.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "보관함 파티 상세 - 채팅 메시지 항목")
data class ArchiveChatMessageResponse(
    @Schema(description = "메시지 ID") val id: Long,
    @Schema(description = "작성자 닉네임 (RealtimeParticipantProfile.nickname). 비정상 데이터면 빈 문자열")
    val authorName: String,
    @Schema(description = "메시지 본문") val content: String,
    @Schema(description = "전송 시각 (KST)") val sentAt: LocalDateTime,
)
```

### Step 4.3: `ArchivePartyDetailResponse.kt` 작성

- [ ] **Create:**

```kotlin
package com.team2.server.party.api.dto

import com.team2.server.party.entity.PartyOption
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "보관함 파티 상세 응답")
data class ArchivePartyDetailResponse(
    @Schema(description = "파티 ID") val partyId: Long,
    @Schema(description = "파티 이름. Party.name이 null이면 빈 문자열") val partyName: String,
    @Schema(description = "REALTIME 또는 PAPER_ONLY") val partyOption: PartyOption,
    @Schema(description = "조회자 역할") val role: ArchiveRole,
    @Schema(description = "파티 시작 시각 (KST)") val partyStartedAt: LocalDateTime,
    @Schema(description = "파티 종료 시각 (KST) = startedAt + 7일") val partyEndedAt: LocalDateTime,
    @Schema(description = "RealtimeParticipantProfile 수. PAPER_ONLY는 0") val participantCount: Long,
    @Schema(description = "롤링페이퍼 총 개수") val paperCount: Long,
    @Schema(description = "참가자 닉네임 목록. PAPER_ONLY는 빈 배열")
    val participants: List<ArchiveParticipantResponse>,
    @Schema(description = "최근 50개 채팅 메시지 (createdAt DESC). PAPER_ONLY는 빈 배열")
    val chatMessages: List<ArchiveChatMessageResponse>,
    @Schema(description = "응답에 담지 못한 추가 메시지 존재 여부") val chatHasMore: Boolean,
    @Schema(description = "본인이 롤페를 작성했는지") val myPaperWritten: Boolean,
    @Schema(description = "본인 롤페 본문. 미작성이면 null") val myPaperContent: String?,
    @Schema(description = "본인 롤페 작성 시 닉네임 스냅샷. 미작성이면 null")
    val myPaperWriterNickname: String?,
    @Schema(description = "본인 롤페 wrapper 이미지 URL. 미작성이면 null") val myPaperWrapperImageUrl: String?,
)

@Schema(description = "보관함 조회자 역할")
enum class ArchiveRole {
    HOST,
    PARTICIPANT,
}
```

### Step 4.4: 컴파일 확인

- [ ] **실행:**

```bash
./gradlew compileKotlin
```

**Expected:** BUILD SUCCESSFUL.

### Step 4.5: 커밋

- [ ] **실행:**

```bash
git add src/main/kotlin/com/team2/server/party/api/dto/
git commit -m "feat: 보관함 파티 상세 응답 DTO 추가"
```

---

## Task 5: `GetArchivedPartyDetailUseCase` 스켈레톤

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/GetArchivedPartyDetailUseCase.kt`

### Step 5.1: UseCase 작성

- [ ] **Create:**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.service.ImageQueryService
import com.team2.server.party.api.dto.ArchiveChatMessageResponse
import com.team2.server.party.api.dto.ArchiveParticipantResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import com.team2.server.party.api.dto.ArchiveRole
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetArchivedPartyDetailUseCase(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val imageQueryService: ImageQueryService,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long,
    ): ArchivePartyDetailResponse {
        val party = partyRepository.findByIdOrNull(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
        val myParticipant = participantRepository.findByPartyIdAndUserId(partyId, userId)
            ?: throw BusinessException(ErrorCode.PARTY_FORBIDDEN)

        val role = if (party.ownerId == userId) ArchiveRole.HOST else ArchiveRole.PARTICIPANT
        val paperCount = rollingPaperRepository.countByParty(party)

        val myPaper = rollingPaperRepository.findByWriter(myParticipant)
        val myPaperWritten = myPaper != null
        val myPaperWrapperImageUrl: String? = myPaper?.let {
            imageQueryService
                .findFirstImageUrlByTargetIds(ImageTargetType.ROLLING_PAPER_WRAPPER, listOf(it.wrapper.id))[it.wrapper.id]
        }

        val (participants, chatMessages, chatHasMore) =
            when (party.partyOption) {
                PartyOption.REALTIME -> buildRealtimeSections(partyId)
                PartyOption.PAPER_ONLY -> EmptySections
            }

        return ArchivePartyDetailResponse(
            partyId = party.id,
            partyName = party.name.orEmpty(),
            partyOption = party.partyOption,
            role = role,
            partyStartedAt = party.startedAt,
            partyEndedAt = party.endedAt(),
            participantCount = participants.size.toLong(),
            paperCount = paperCount,
            participants = participants,
            chatMessages = chatMessages,
            chatHasMore = chatHasMore,
            myPaperWritten = myPaperWritten,
            myPaperContent = myPaper?.content,
            myPaperWriterNickname = myPaper?.writerNickname,
            myPaperWrapperImageUrl = myPaperWrapperImageUrl,
        )
    }

    private fun buildRealtimeSections(partyId: Long): Sections {
        val profiles = realtimeParticipantProfileRepository.findAllByPartyIdOrderByIdAsc(partyId)
        val participants = profiles.map { ArchiveParticipantResponse(nickname = it.nickname) }

        val chatTotal = chatMessageRepository.countByPartyId(partyId)
        val recentChat =
            chatMessageRepository.findRecentByPartyId(partyId, PageRequest.of(0, CHAT_RECENT_LIMIT))
        val participantIds = recentChat.map { it.participant.id }.toSet()
        val profileByParticipantId =
            if (participantIds.isEmpty()) {
                emptyMap()
            } else {
                realtimeParticipantProfileRepository
                    .findAllByParticipantIdIn(participantIds)
                    .associateBy { it.participant.id }
            }
        val chatMessages =
            recentChat.map { msg ->
                ArchiveChatMessageResponse(
                    id = msg.id,
                    authorName = profileByParticipantId[msg.participant.id]?.nickname.orEmpty(),
                    content = msg.content,
                    sentAt = msg.createdAt,
                )
            }
        val chatHasMore = chatTotal > chatMessages.size
        return Sections(participants, chatMessages, chatHasMore)
    }

    private data class Sections(
        val participants: List<ArchiveParticipantResponse>,
        val chatMessages: List<ArchiveChatMessageResponse>,
        val chatHasMore: Boolean,
    )

    companion object {
        const val CHAT_RECENT_LIMIT: Int = 50
        private val EmptySections =
            Sections(participants = emptyList(), chatMessages = emptyList(), chatHasMore = false)
    }
}
```

> 주의: `EmptySections`는 `companion object` 안의 private val이지만 `when` 분기에서 직접 destructure할 수 있도록 같은 클래스 안에 있어야 한다. 코드를 그대로 옮기되, Kotlin이 internal destructure를 허용하지 않으면 `val sections = when(...) { ... }; val (participants, chatMessages, chatHasMore) = sections` 로 두 줄로 풀어 적는다.

### Step 5.2: 컴파일 확인

- [ ] **실행:** `./gradlew compileKotlin`

**Expected:** BUILD SUCCESSFUL.

만약 destructure 관련 에러가 나면 `invoke` 안의 when 결과를 `val sections = ...` 변수에 받아 두 줄로 변경한다.

### Step 5.3: 커밋

- [ ] **실행:**

```bash
git add src/main/kotlin/com/team2/server/party/application/usecase/GetArchivedPartyDetailUseCase.kt
git commit -m "feat: GetArchivedPartyDetailUseCase 추가"
```

---

## Task 6: Swagger API interface + Controller

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/ArchivePartyDetailApi.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/ArchivePartyDetailController.kt`

### Step 6.1: API 인터페이스 작성

- [ ] **Create:**

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.ForbiddenResponse
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable

@Tag(name = "Archive", description = "보관함 API")
interface ArchivePartyDetailApi {
    @Operation(
        summary = "보관함 파티 상세 조회",
        description = """
            보관함 화면에서 한 파티 상세를 조회한다. 본인이 작성한 롤페가 있으면 모달용 4개 필드가 함께 채워진다.
            PAPER_ONLY 파티는 participants/chatMessages가 빈 배열, participantCount=0, chatHasMore=false 로 응답한다.
        """,
    )
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun getArchivedPartyDetail(
        @Parameter(description = "파티 ID", required = true)
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchivePartyDetailResponse>
}
```

### Step 6.2: Controller 작성

- [ ] **Create:**

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.api.dto.ArchivePartyDetailResponse
import com.team2.server.party.application.usecase.GetArchivedPartyDetailUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/archive")
class ArchivePartyDetailController(
    private val getArchivedPartyDetailUseCase: GetArchivedPartyDetailUseCase,
) : ArchivePartyDetailApi {
    @GetMapping("/party/{partyId}")
    override fun getArchivedPartyDetail(
        @PathVariable partyId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<ArchivePartyDetailResponse> =
        ApiResponse.success(getArchivedPartyDetailUseCase.invoke(partyId, principal.userId))
}
```

### Step 6.3: 컴파일 확인

- [ ] **실행:** `./gradlew compileKotlin`

**Expected:** BUILD SUCCESSFUL.

### Step 6.4: SecurityConfig 영향 확인

- [ ] **확인:** `src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt` 를 grep해서 `/api/v1/archive` 경로에 대한 permitAll 매처가 있는지 검사.

```bash
grep -n "archive" src/main/kotlin/com/team2/server/auth/config/SecurityConfig.kt
```

**Expected:** 매칭 없음. 매칭이 있고 패턴이 `/api/v1/archive/**` 같이 너무 넓다면 별도 PR 의존성이므로 이 plan에서는 손대지 않고, Task 8에서 401 검증 시 실제 거동으로 확인한다.

### Step 6.5: 커밋

- [ ] **실행:**

```bash
git add src/main/kotlin/com/team2/server/party/api/
git commit -m "feat: 보관함 파티 상세 API 컨트롤러와 스웨거 인터페이스 추가"
```

---

## Task 7: Controller 통합 테스트 (성공 시나리오)

**Files:**
- Create: `src/test/kotlin/com/team2/server/party/api/ArchivePartyDetailControllerTest.kt`

### Step 7.1: 테스트 스켈레톤 작성

- [ ] **Create:** 파일 상단부와 헬퍼 메서드.

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.chat.entity.ChatMessage
import com.team2.server.chat.repository.ChatMessageRepository
import com.team2.server.common.DatabaseCleanup
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.entity.RollingPaper
import com.team2.server.rollingpaper.entity.RollingPaperWrapper
import com.team2.server.rollingpaper.repository.RollingPaperRepository
import com.team2.server.rollingpaper.repository.RollingPaperWrapperRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class ArchivePartyDetailControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val rollingPaperRepository: RollingPaperRepository,
        private val rollingPaperWrapperRepository: RollingPaperWrapperRepository,
        private val chatMessageRepository: ChatMessageRepository,
        private val userRepository: UserRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        private fun saveUser(email: String): User =
            userRepository.save(User(email = email, provider = AuthProvider.KAKAO, providerId = email))

        private fun token(user: User): String = tokenProvider.issue(user)
    }
```

### Step 7.2: REALTIME 참가자 성공 시나리오 추가

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `REALTIME 파티 참가자가 본인이 작성한 경우 상세 응답`() {
    val owner = saveUser("owner@x")
    val me = saveUser("me@x")
    val party =
        partyRepository.save(
            RealtimeParty(
                ownerId = owner.id,
                name = "김유빈의 파티",
                celebrantNickname = "김유빈",
                startedAt = LocalDateTime.now().minusDays(1),
            ),
        )
    val ownerParticipant = participantRepository.save(Participant(party = party, user = owner))
    val myParticipant = participantRepository.save(Participant(party = party, user = me))
    profileRepository.save(RealtimeParticipantProfile(participant = ownerParticipant, nickname = "주최자"))
    profileRepository.save(RealtimeParticipantProfile(participant = myParticipant, nickname = "해파리"))
    val wrapper = rollingPaperWrapperRepository.save(RollingPaperWrapper(name = "Topping_Candle"))
    rollingPaperRepository.save(
        RollingPaper(
            wrapper = wrapper,
            writer = myParticipant,
            party = party,
            writerNickname = "해파리",
            content = "축하해",
        ),
    )

    mockMvc
        .get("/api/v1/archive/party/${party.id}") {
            header("Authorization", "Bearer ${token(me)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.partyId") { value(party.id) }
            jsonPath("$.data.partyName") { value("김유빈의 파티") }
            jsonPath("$.data.partyOption") { value("REALTIME") }
            jsonPath("$.data.role") { value("PARTICIPANT") }
            jsonPath("$.data.participantCount") { value(2) }
            jsonPath("$.data.paperCount") { value(1) }
            jsonPath("$.data.participants[0].nickname") { value("주최자") }
            jsonPath("$.data.participants[1].nickname") { value("해파리") }
            jsonPath("$.data.chatMessages.length()") { value(0) }
            jsonPath("$.data.chatHasMore") { value(false) }
            jsonPath("$.data.myPaperWritten") { value(true) }
            jsonPath("$.data.myPaperContent") { value("축하해") }
            jsonPath("$.data.myPaperWriterNickname") { value("해파리") }
        }
}
```

### Step 7.3: REALTIME 주최자 시나리오 추가

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `REALTIME 파티 주최자는 role이 HOST`() {
    val owner = saveUser("owner@x")
    val party =
        partyRepository.save(
            RealtimeParty(
                ownerId = owner.id,
                name = "p",
                celebrantNickname = "홍",
                startedAt = LocalDateTime.now(),
            ),
        )
    participantRepository.save(Participant(party = party, user = owner))

    mockMvc
        .get("/api/v1/archive/party/${party.id}") {
            header("Authorization", "Bearer ${token(owner)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.role") { value("HOST") }
            jsonPath("$.data.myPaperWritten") { value(false) }
            jsonPath("$.data.myPaperContent") { value(org.hamcrest.Matchers.nullValue()) }
        }
}
```

### Step 7.4: PAPER_ONLY 시나리오 추가

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `PAPER_ONLY 파티는 participants와 chatMessages가 빈 배열`() {
    val me = saveUser("me@x")
    val party =
        partyRepository.save(
            PaperOnlyParty(
                ownerId = 99L,
                name = "김유빈의 롤링페이퍼",
                celebrantNickname = "김유빈",
                startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
            ),
        )
    participantRepository.save(Participant(party = party, user = me))

    mockMvc
        .get("/api/v1/archive/party/${party.id}") {
            header("Authorization", "Bearer ${token(me)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.partyOption") { value("PAPER_ONLY") }
            jsonPath("$.data.participants.length()") { value(0) }
            jsonPath("$.data.participantCount") { value(0) }
            jsonPath("$.data.chatMessages.length()") { value(0) }
            jsonPath("$.data.chatHasMore") { value(false) }
        }
}
```

### Step 7.5: 채팅 50개 cap 시나리오 추가

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `채팅 60개면 최근 50개와 chatHasMore true 반환`() {
    val me = saveUser("me@x")
    val party =
        partyRepository.save(
            RealtimeParty(
                ownerId = 99L,
                name = "p",
                celebrantNickname = "홍",
                startedAt = LocalDateTime.now().minusDays(1),
            ),
        )
    val myParticipant = participantRepository.save(Participant(party = party, user = me))
    profileRepository.save(RealtimeParticipantProfile(participant = myParticipant, nickname = "해파리"))
    repeat(60) { idx ->
        chatMessageRepository.save(ChatMessage(content = "m$idx", party = party, participant = myParticipant))
    }

    mockMvc
        .get("/api/v1/archive/party/${party.id}") {
            header("Authorization", "Bearer ${token(me)}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.chatMessages.length()") { value(50) }
            jsonPath("$.data.chatHasMore") { value(true) }
            jsonPath("$.data.chatMessages[0].content") { value("m59") }
            jsonPath("$.data.chatMessages[0].authorName") { value("해파리") }
        }
}
```

### Step 7.6: 테스트 실행

- [ ] **실행:**

```bash
./gradlew test --tests com.team2.server.party.api.ArchivePartyDetailControllerTest
```

**Expected:** 4 tests passed.

실패가 발생하면 UseCase 흐름과 매핑을 확인한다. 흔한 원인:

- `RealtimeParticipantProfile` `id ASC` 정렬 실패 → JPQL 수정
- chat 채팅 작성자의 `RealtimeParticipantProfile` 미생성으로 인한 `authorName=""` 결과 → 테스트에서 프로필 saving 누락 점검

### Step 7.7: 커밋

- [ ] **실행:**

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchivePartyDetailControllerTest.kt
git commit -m "test: 보관함 파티 상세 성공 시나리오 통합 테스트 추가"
```

---

## Task 8: 권한/에러 통합 테스트

**Files:**
- Modify: `src/test/kotlin/com/team2/server/party/api/ArchivePartyDetailControllerTest.kt`

### Step 8.1: 비로그인 401 테스트 추가

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `Authorization 헤더가 없으면 401`() {
    val party =
        partyRepository.save(
            RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
        )

    mockMvc.get("/api/v1/archive/party/${party.id}").andExpect {
        status { isUnauthorized() }
    }
}

@Test
fun `잘못된 Bearer 토큰이면 401`() {
    val party =
        partyRepository.save(
            RealtimeParty(ownerId = 1L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
        )

    mockMvc
        .get("/api/v1/archive/party/${party.id}") {
            header("Authorization", "Bearer not-a-jwt")
        }.andExpect {
            status { isUnauthorized() }
        }
}
```

### Step 8.2: 403 시나리오 추가 (다른 파티 회원)

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `해당 파티 participant가 아니면 403 PARTY_FORBIDDEN`() {
    val outsider = saveUser("outsider@x")
    val party =
        partyRepository.save(
            RealtimeParty(ownerId = 999L, name = "p", celebrantNickname = "홍", startedAt = LocalDateTime.now()),
        )

    mockMvc
        .get("/api/v1/archive/party/${party.id}") {
            header("Authorization", "Bearer ${token(outsider)}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
        }
}
```

### Step 8.3: 404 시나리오 추가 (없는 partyId)

- [ ] **Edit:** 클래스 내부에 다음 테스트 추가.

```kotlin
@Test
fun `없는 partyId이면 404 PARTY_NOT_FOUND`() {
    val me = saveUser("me@x")

    mockMvc
        .get("/api/v1/archive/party/99999") {
            header("Authorization", "Bearer ${token(me)}")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
        }
}
```

### Step 8.4: 테스트 실행

- [ ] **실행:**

```bash
./gradlew test --tests com.team2.server.party.api.ArchivePartyDetailControllerTest
```

**Expected:** 7 tests passed (Task 7 + Task 8 합산).

### Step 8.5: 커밋

- [ ] **실행:**

```bash
git add src/test/kotlin/com/team2/server/party/api/ArchivePartyDetailControllerTest.kt
git commit -m "test: 보관함 파티 상세 인증과 권한 케이스 추가"
```

---

## Task 9: 전체 검증 + 마무리

### Step 9.1: 전체 테스트 실행

- [ ] **실행:**

```bash
./gradlew test
```

**Expected:** BUILD SUCCESSFUL. 신규 케이스 외 다른 테스트도 모두 통과.

실패가 있다면 PR 영향 범위에 의한 회귀일 가능성이 높다. 새로 추가한 Repository 메서드의 N+1 또는 fetch join이 기존 cascade와 충돌하는지 점검한다.

### Step 9.2: 커밋 정리 및 push 준비

- [ ] **실행:** `git log --oneline develop..HEAD` 로 커밋 목록 확인. 각 커밋은 한 가지 의미를 담아야 한다. fixup 필요한 게 보이면 작업자가 직접 정리.

### Step 9.3: PR 생성 가이드

- [ ] PR 생성은 사용자에게 묻고 진행한다. 진행한다면:
  - base: `develop`
  - `/team-pr` 스킬 사용 권장 (팀 PR 템플릿 자동 적용)

---

## 자가 점검

이 plan을 spec과 대조해 검증한 항목:

- [x] `GET /api/v1/archive/party/{partyId}` 단일 API → Task 6
- [x] 응답 모든 필드 (`partyId`/`partyName`/`partyOption`/`role`/`partyStartedAt`/`partyEndedAt`/`participantCount`/`paperCount`/`participants[]`/`chatMessages[]`/`chatHasMore`/`myPaperWritten`/`myPaperContent`/`myPaperWriterNickname`/`myPaperWrapperImageUrl`) → Task 4 DTO + Task 5 매핑 + Task 7 검증
- [x] 권한 401/403/404 → Task 8
- [x] PAPER_ONLY는 participants=[], participantCount=0, chatMessages=[], chatHasMore=false → Task 5 분기 + Task 7.4
- [x] 채팅 cap 50 + chatHasMore → Task 5 상수 + Task 7.5
- [x] `myPaperWritten=false`이면 3개 필드 null → Task 5 매핑 + Task 7.3
- [x] `RealtimeParticipantProfile.nickname` 기준 participantCount → Task 5
- [x] 패키지 위치 `party/api`, `party/application/usecase` → Task 4~6
- [x] 신규 ErrorCode 없음 → Task 5 (기존 재사용)
- [x] 헤더 이미지 응답 미포함 → DTO에 필드 없음

타입/메서드 일관성:
- `ArchiveRole` enum 값 `HOST` / `PARTICIPANT` 으로 통일.
- `findByWriter(participant)` 시그니처 Task 2/Task 5 일치.
- `CHAT_RECENT_LIMIT = 50` 상수가 Task 5 본문에 정의 + Task 7.5 단언 일치.
- `findAllByPartyIdOrderByIdAsc` / `findAllByParticipantIdIn` 메서드명 Task 1/Task 5 일치.
