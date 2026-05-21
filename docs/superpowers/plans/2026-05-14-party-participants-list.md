# 파티 참여자 목록 조회 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 실시간 파티 진행 기본화면용 참여자 목록 조회 API (`GET /api/v1/parties/{partyId}/participants`) 를 RealtimeParty 전용으로 추가한다.

**Architecture:** Archive(#127) PR 패턴을 따라 Controller 는 `party/api/`, UseCase 는 `party/application/usecase/`, Application DTO 는 `party/dto/` (Result 접미사), Response DTO 는 `party/api/dto/` 에 배치. Service 는 기존 `party/service/`, Repository 는 `party/repository/` 그대로 활용. Controller → UseCase → (PartyService + ParticipantService) → Repository 의 단방향 의존만 사용한다.

**Tech Stack:** Kotlin 2.x, Spring Boot 3.x, Spring Data JPA, JPQL JOIN FETCH, Testcontainers (MySQL 8.0), JUnit 5, MockMvc.

---

> **Spec 과 실제 패키지 차이**: spec 문서는 향후 리팩토링 후 새 레이아웃 (`domain/entity/`, `infrastructure/persistence/`, `application/service/`) 을 가정했으나, 현재 develop 은 옛 레이아웃 (`entity/`, `repository/`, `service/`) 이라 본 plan 은 실제 경로 기준이다. 의도된 기능·API 형태·에러 정책은 spec 과 동일.

## File Map

| 종류 | 경로 |
|---|---|
| 수정 | `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` |
| 수정 | `src/main/kotlin/com/team2/server/party/entity/RealtimeParty.kt` |
| 수정 | `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt` |
| 수정 | `src/main/kotlin/com/team2/server/party/service/PartyService.kt` |
| 수정 | `src/main/kotlin/com/team2/server/party/service/ParticipantService.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/dto/PartyParticipantsResult.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/dto/PartyParticipantResult.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCase.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/api/dto/PartyParticipantsResponse.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/api/dto/PartyParticipantResponse.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/api/ParticipantApi.kt` |
| 신규 | `src/main/kotlin/com/team2/server/party/api/ParticipantController.kt` |
| 신규 | `src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt` |
| 신규 | `src/test/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCaseTest.kt` |
| 신규 | `src/test/kotlin/com/team2/server/party/api/ParticipantControllerTest.kt` |

---

## Task 1: ErrorCode 추가 (`PARTY_NOT_REALTIME`)

**Files:**
- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`

- [ ] **Step 1: enum 항목 추가**

`PARTY_ENDED` 줄 바로 아래에 추가:

```kotlin
PARTY_NOT_REALTIME(HttpStatus.BAD_REQUEST, "실시간 파티가 아닙니다"),
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt
git commit -m "feat: PARTY_NOT_REALTIME 에러 코드 추가"
```

---

## Task 2: RealtimeParty MAX_PARTICIPANTS 상수 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/entity/RealtimeParty.kt`

- [ ] **Step 1: companion object 상수 추가**

기존 `companion object` 블록을 다음으로 교체:

```kotlin
    companion object {
        const val LIVE_DURATION_MINUTES: Long = 10
        const val ENTERABLE_BEFORE_MINUTES: Long = 5
        const val MAX_PARTICIPANTS: Int = 14
    }
```

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/entity/RealtimeParty.kt
git commit -m "feat: RealtimeParty 최대 참가자 수 상수 추가"
```

---

## Task 3: RealtimeParticipantProfileRepository 쿼리 추가 + 테스트

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt`
- Create: `src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

새 파일 `src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt`:

```kotlin
package com.team2.server.party.repository

import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime

@DataJpaTest
@Import(TestcontainersConfiguration::class)
class RealtimeParticipantProfileRepositoryTest
    @Autowired
    constructor(
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val participantRepository: ParticipantRepository,
        private val partyRepository: PartyRepository,
        private val characterRepository: CharacterRepository,
        private val userRepository: UserRepository,
    ) {
        @Test
        fun `partyId 기준 입장 순서대로 프로필을 조회한다`() {
            val owner = userRepository.save(User(provider = AuthProvider.GOOGLE, providerId = "o", email = "o@e", name = "o"))
            val party =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "주최자",
                        startedAt = LocalDateTime.now().plusHours(1),
                    ),
                )
            val character = characterRepository.save(Character(name = "octopus"))
            val ownerParticipant =
                participantRepository.save(Participant(party = party, user = owner, isCelebrant = true))
            val ownerProfile =
                profileRepository.save(
                    RealtimeParticipantProfile(
                        participant = ownerParticipant,
                        nickname = "주최자",
                        character = character,
                    ),
                )
            val memberUser =
                userRepository.save(User(provider = AuthProvider.GOOGLE, providerId = "m", email = "m@e", name = "m"))
            val memberParticipant =
                participantRepository.save(Participant(party = party, user = memberUser))
            val memberProfile =
                profileRepository.save(
                    RealtimeParticipantProfile(
                        participant = memberParticipant,
                        nickname = "참가자A",
                        character = character,
                    ),
                )

            val result = profileRepository.findAllByPartyIdOrderByParticipantIdAsc(party.id)

            assertThat(result).extracting<Long> { it.participant.id }
                .containsExactly(ownerParticipant.id, memberParticipant.id)
            assertThat(result).extracting<String> { it.nickname }
                .containsExactly("주최자", "참가자A")
            assertThat(result[0].id).isEqualTo(ownerProfile.id)
            assertThat(result[1].id).isEqualTo(memberProfile.id)
        }

        @Test
        fun `다른 파티의 프로필은 제외된다`() {
            val owner = userRepository.save(User(provider = AuthProvider.GOOGLE, providerId = "o", email = "o@e", name = "o"))
            val partyA =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "A",
                        startedAt = LocalDateTime.now().plusHours(1),
                    ),
                )
            val partyB =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "B",
                        startedAt = LocalDateTime.now().plusHours(2),
                    ),
                )
            val character = characterRepository.save(Character(name = "octopus"))
            val pA = participantRepository.save(Participant(party = partyA, user = owner, isCelebrant = true))
            profileRepository.save(RealtimeParticipantProfile(participant = pA, nickname = "A주최", character = character))
            val pB = participantRepository.save(Participant(party = partyB, user = owner, isCelebrant = true))
            profileRepository.save(RealtimeParticipantProfile(participant = pB, nickname = "B주최", character = character))

            val result = profileRepository.findAllByPartyIdOrderByParticipantIdAsc(partyA.id)

            assertThat(result).hasSize(1)
            assertThat(result[0].nickname).isEqualTo("A주최")
        }
    }
```

- [ ] **Step 2: 테스트 실행으로 실패 확인**

```bash
DOCKER_HOST=unix:///Users/choetaegyu/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew test --tests "com.team2.server.party.repository.RealtimeParticipantProfileRepositoryTest"
```

Expected: 컴파일 실패 — `findAllByPartyIdOrderByParticipantIdAsc` 메서드 없음

- [ ] **Step 3: Repository 메서드 추가**

`RealtimeParticipantProfileRepository.kt` 의 인터페이스 body 끝에 추가:

```kotlin
    @org.springframework.data.jpa.repository.Query(
        """
        SELECT rpp
        FROM RealtimeParticipantProfile rpp
        JOIN FETCH rpp.participant participant
        LEFT JOIN FETCH participant.user
        LEFT JOIN FETCH rpp.character
        WHERE participant.party.id = :partyId
        ORDER BY participant.id ASC
        """,
    )
    fun findAllByPartyIdOrderByParticipantIdAsc(partyId: Long): List<RealtimeParticipantProfile>
```

> 기존 import 의 `@Query` 가 없다면 파일 상단 import 에 `import org.springframework.data.jpa.repository.Query` 를 추가하고 어노테이션을 `@Query(...)` 로 줄여도 좋다.

- [ ] **Step 4: 테스트 통과 확인**

```bash
DOCKER_HOST=unix:///Users/choetaegyu/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew test --tests "com.team2.server.party.repository.RealtimeParticipantProfileRepositoryTest"
```

Expected: BUILD SUCCESSFUL, 2 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt \
        src/test/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepositoryTest.kt
git commit -m "feat: 파티 ID 기준 RealtimeParticipantProfile 순서 조회 쿼리 추가"
```

---

## Task 4: PartyService.requireRealtimeParty 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/service/PartyService.kt`

- [ ] **Step 1: import 추가 (필요시)**

`PartyService.kt` 상단 import 에 다음이 없으면 추가:

```kotlin
import com.team2.server.party.entity.PartyOption
import org.hibernate.Hibernate
```

- [ ] **Step 2: 메서드 추가**

`findUser` 메서드 위 (private 메서드 그룹 바로 위) 에 추가:

```kotlin
    fun requireRealtimeParty(partyId: Long): RealtimeParty {
        val party = findParty(partyId)
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
        return Hibernate.unproxy(party) as RealtimeParty
    }
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/service/PartyService.kt
git commit -m "feat: PartyService 에 실시간 파티 검증 메서드 추가"
```

---

## Task 5: ParticipantService 에 requireParticipant + findOrderedProfiles 추가

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/service/ParticipantService.kt`

- [ ] **Step 1: import 및 의존성 추가**

`ParticipantService.kt` 상단 import 에 추가:

```kotlin
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
```

생성자 파라미터에 의존성 추가:

```kotlin
@Service
class ParticipantService(
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
) {
```

- [ ] **Step 2: 메서드 추가**

클래스 body 끝(`companion object` 직전) 에 추가:

```kotlin
    fun requireParticipant(
        partyId: Long,
        userId: Long,
    ) {
        if (!participantRepository.existsByPartyIdAndUserId(partyId, userId)) {
            throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        }
    }

    fun findOrderedProfiles(partyId: Long): List<RealtimeParticipantProfile> =
        realtimeParticipantProfileRepository.findAllByPartyIdOrderByParticipantIdAsc(partyId)
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/service/ParticipantService.kt
git commit -m "feat: ParticipantService 에 참여자 검증·정렬 조회 메서드 추가"
```

---

## Task 6: Application DTO (`PartyParticipantsResult`, `PartyParticipantResult`)

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/dto/PartyParticipantResult.kt`
- Create: `src/main/kotlin/com/team2/server/party/dto/PartyParticipantsResult.kt`

- [ ] **Step 1: PartyParticipantResult 생성**

```kotlin
package com.team2.server.party.dto

data class PartyParticipantResult(
    val participantId: Long,
    val joinOrder: Int,
    val nickname: String,
    val characterId: Long?,
    val characterImageUrl: String?,
    val isOwner: Boolean,
    val isCelebrant: Boolean,
    val isMe: Boolean,
)
```

- [ ] **Step 2: PartyParticipantsResult 생성**

```kotlin
package com.team2.server.party.dto

data class PartyParticipantsResult(
    val totalCount: Int,
    val maxCount: Int,
    val participants: List<PartyParticipantResult>,
)
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/dto/PartyParticipantResult.kt \
        src/main/kotlin/com/team2/server/party/dto/PartyParticipantsResult.kt
git commit -m "feat: 파티 참여자 목록 응용 DTO 추가"
```

---

## Task 7: GetPartyParticipantsUseCase + 단위 테스트

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCase.kt`
- Create: `src/test/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCaseTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.service.ParticipantService
import com.team2.server.party.service.PartyService
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime

class GetPartyParticipantsUseCaseTest {
    private val partyService = mock(PartyService::class.java)
    private val participantService = mock(ParticipantService::class.java)
    private val imageRepository = mock(ImageRepository::class.java)
    private val useCase = GetPartyParticipantsUseCase(partyService, participantService, imageRepository)

    @Test
    fun `주최자 호출 시 joinOrder는 참가자 id 오름차순으로 부여되고 isOwner 가 표시된다`() {
        val party =
            RealtimeParty(
                ownerId = 10L,
                celebrantNickname = "주최자",
                startedAt = LocalDateTime.now().plusHours(1),
            ).withId(100L)
        val owner = newUser(10L)
        val memberUser = newUser(20L)
        val ownerParticipant = newParticipant(1L, party, owner, isCelebrant = true)
        val memberParticipant = newParticipant(2L, party, memberUser, isCelebrant = false)
        val character = newCharacter(7L)
        val ownerProfile = newProfile(11L, ownerParticipant, "주최닉", character)
        val memberProfile = newProfile(12L, memberParticipant, "참가닉", character)

        `when`(partyService.requireRealtimeParty(100L)).thenReturn(party)
        `when`(participantService.findOrderedProfiles(100L)).thenReturn(listOf(ownerProfile, memberProfile))
        `when`(
            imageRepository.findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(
                ImageTargetType.CHARACTER,
                listOf(7L),
            ),
        ).thenReturn(listOf(newImage(7L, "https://cdn/c7.png", 0)))

        val result = useCase.invoke(partyId = 100L, userId = 10L)

        assertThat(result.totalCount).isEqualTo(2)
        assertThat(result.maxCount).isEqualTo(RealtimeParty.MAX_PARTICIPANTS)
        assertThat(result.participants[0].joinOrder).isEqualTo(1)
        assertThat(result.participants[0].isOwner).isTrue()
        assertThat(result.participants[0].isCelebrant).isTrue()
        assertThat(result.participants[0].isMe).isTrue()
        assertThat(result.participants[0].characterImageUrl).isEqualTo("https://cdn/c7.png")
        assertThat(result.participants[1].joinOrder).isEqualTo(2)
        assertThat(result.participants[1].isOwner).isFalse()
        assertThat(result.participants[1].isCelebrant).isFalse()
        assertThat(result.participants[1].isMe).isFalse()
    }

    @Test
    fun `참가자 본인이 호출하면 본인의 isMe 만 true`() {
        val party = RealtimeParty(ownerId = 10L, celebrantNickname = "x", startedAt = LocalDateTime.now().plusHours(1)).withId(100L)
        val ownerParticipant = newParticipant(1L, party, newUser(10L), isCelebrant = true)
        val meParticipant = newParticipant(2L, party, newUser(20L), isCelebrant = false)
        val character = newCharacter(7L)
        `when`(partyService.requireRealtimeParty(100L)).thenReturn(party)
        `when`(participantService.findOrderedProfiles(100L)).thenReturn(
            listOf(
                newProfile(11L, ownerParticipant, "주최", character),
                newProfile(12L, meParticipant, "나", character),
            ),
        )
        `when`(
            imageRepository.findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(
                ImageTargetType.CHARACTER,
                listOf(7L),
            ),
        ).thenReturn(emptyList())

        val result = useCase.invoke(partyId = 100L, userId = 20L)

        assertThat(result.participants[0].isMe).isFalse()
        assertThat(result.participants[1].isMe).isTrue()
        assertThat(result.participants[1].characterImageUrl).isNull()
    }

    @Test
    fun `비참가자 호출 시 ParticipantService 가 PARTY_FORBIDDEN 던지면 전파한다`() {
        val party = RealtimeParty(ownerId = 10L, celebrantNickname = "x", startedAt = LocalDateTime.now().plusHours(1)).withId(100L)
        `when`(partyService.requireRealtimeParty(100L)).thenReturn(party)
        `when`(participantService.requireParticipant(100L, 99L))
            .thenThrow(BusinessException(ErrorCode.PARTY_FORBIDDEN))

        assertThatThrownBy { useCase.invoke(partyId = 100L, userId = 99L) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTY_FORBIDDEN)
    }

    @Test
    fun `PaperOnly 파티는 PartyService 에서 PARTY_NOT_REALTIME 을 던지고 전파한다`() {
        `when`(partyService.requireRealtimeParty(100L))
            .thenThrow(BusinessException(ErrorCode.PARTY_NOT_REALTIME))

        assertThatThrownBy { useCase.invoke(partyId = 100L, userId = 10L) }
            .isInstanceOf(BusinessException::class.java)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PARTY_NOT_REALTIME)
    }

    private fun newUser(id: Long) =
        User(provider = AuthProvider.GOOGLE, providerId = "p$id", email = "e$id@e", name = "n$id").withId(id)

    private fun newParticipant(
        id: Long,
        party: RealtimeParty,
        user: User,
        isCelebrant: Boolean,
    ) = Participant(party = party, user = user, isCelebrant = isCelebrant).withId(id)

    private fun newCharacter(id: Long) = Character(name = "c$id").withId(id)

    private fun newProfile(
        id: Long,
        participant: Participant,
        nickname: String,
        character: Character,
    ) = RealtimeParticipantProfile(participant = participant, nickname = nickname, character = character).withId(id)

    private fun newImage(
        targetId: Long,
        url: String,
        order: Int,
    ) = Image(imageUrl = url, targetType = ImageTargetType.CHARACTER, targetId = targetId, sortOrder = order).withId(targetId)

    private fun <T : Any> T.withId(id: Long): T = apply { ReflectionTestUtils.setField(this, "id", id) }
}
```

- [ ] **Step 2: 테스트 실행으로 실패 확인**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.GetPartyParticipantsUseCaseTest"
```

Expected: 컴파일 실패 — `GetPartyParticipantsUseCase` 클래스 없음

- [ ] **Step 3: UseCase 구현**

`src/main/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCase.kt`:

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.party.dto.PartyParticipantResult
import com.team2.server.party.dto.PartyParticipantsResult
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.service.ParticipantService
import com.team2.server.party.service.PartyService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetPartyParticipantsUseCase(
    private val partyService: PartyService,
    private val participantService: ParticipantService,
    private val imageRepository: ImageRepository,
) {
    @Transactional(readOnly = true)
    fun invoke(
        partyId: Long,
        userId: Long,
    ): PartyParticipantsResult {
        val party = partyService.requireRealtimeParty(partyId)
        participantService.requireParticipant(partyId, userId)

        val profiles = participantService.findOrderedProfiles(partyId)
        val characterIds = profiles.mapNotNull { it.character?.id }.distinct()
        val imageUrlByCharacterId =
            if (characterIds.isEmpty()) {
                emptyMap()
            } else {
                imageRepository
                    .findAllByTargetTypeAndTargetIdsOrderByTargetIdAndSortOrder(ImageTargetType.CHARACTER, characterIds)
                    .filter { it.sortOrder == CHARACTER_IMAGE_SORT_ORDER }
                    .associate { it.targetId to it.imageUrl }
            }

        val items =
            profiles.mapIndexed { index, profile ->
                val participant = profile.participant
                PartyParticipantResult(
                    participantId = participant.id,
                    joinOrder = index + 1,
                    nickname = profile.nickname,
                    characterId = profile.character?.id,
                    characterImageUrl = profile.character?.id?.let { imageUrlByCharacterId[it] },
                    isOwner = participant.user?.id == party.ownerId,
                    isCelebrant = participant.isCelebrant,
                    isMe = participant.user?.id == userId,
                )
            }
        return PartyParticipantsResult(
            totalCount = items.size,
            maxCount = RealtimeParty.MAX_PARTICIPANTS,
            participants = items,
        )
    }

    private companion object {
        private const val CHARACTER_IMAGE_SORT_ORDER = 0
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.GetPartyParticipantsUseCaseTest"
```

Expected: BUILD SUCCESSFUL, 4 tests passed

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCase.kt \
        src/test/kotlin/com/team2/server/party/application/usecase/GetPartyParticipantsUseCaseTest.kt
git commit -m "feat: 파티 참여자 목록 조회 UseCase 추가"
```

---

## Task 8: API Response DTO

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/dto/PartyParticipantResponse.kt`
- Create: `src/main/kotlin/com/team2/server/party/api/dto/PartyParticipantsResponse.kt`

- [ ] **Step 1: PartyParticipantResponse 생성**

```kotlin
package com.team2.server.party.api.dto

import com.team2.server.party.dto.PartyParticipantResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "파티 참여자 항목")
data class PartyParticipantResponse(
    @Schema(description = "참여자 ID", example = "17")
    val participantId: Long,
    @Schema(description = "입장 순서 (1부터)", example = "1")
    val joinOrder: Int,
    @Schema(description = "닉네임", example = "주최자닉")
    val nickname: String,
    @Schema(description = "캐릭터 ID", example = "3", nullable = true)
    val characterId: Long?,
    @Schema(description = "캐릭터 메인 이미지 URL", nullable = true)
    val characterImageUrl: String?,
    @Schema(description = "파티 주최자 여부", example = "true")
    val isOwner: Boolean,
    @Schema(description = "파티 주인공 여부", example = "true")
    val isCelebrant: Boolean,
    @Schema(description = "조회자 본인 여부", example = "false")
    val isMe: Boolean,
) {
    companion object {
        fun from(result: PartyParticipantResult): PartyParticipantResponse =
            PartyParticipantResponse(
                participantId = result.participantId,
                joinOrder = result.joinOrder,
                nickname = result.nickname,
                characterId = result.characterId,
                characterImageUrl = result.characterImageUrl,
                isOwner = result.isOwner,
                isCelebrant = result.isCelebrant,
                isMe = result.isMe,
            )
    }
}
```

- [ ] **Step 2: PartyParticipantsResponse 생성**

```kotlin
package com.team2.server.party.api.dto

import com.team2.server.party.dto.PartyParticipantsResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "파티 참여자 목록 응답")
data class PartyParticipantsResponse(
    @Schema(description = "현재 참여자 수", example = "4")
    val totalCount: Int,
    @Schema(description = "최대 참여자 수", example = "14")
    val maxCount: Int,
    @Schema(description = "입장 순서대로 정렬된 참여자 목록")
    val participants: List<PartyParticipantResponse>,
) {
    companion object {
        fun from(result: PartyParticipantsResult): PartyParticipantsResponse =
            PartyParticipantsResponse(
                totalCount = result.totalCount,
                maxCount = result.maxCount,
                participants = result.participants.map(PartyParticipantResponse::from),
            )
    }
}
```

- [ ] **Step 3: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/api/dto/PartyParticipantResponse.kt \
        src/main/kotlin/com/team2/server/party/api/dto/PartyParticipantsResponse.kt
git commit -m "feat: 파티 참여자 목록 API 응답 DTO 추가"
```

---

## Task 9: ParticipantApi (Swagger interface)

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/ParticipantApi.kt`

- [ ] **Step 1: 인터페이스 생성**

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.ForbiddenResponse
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.party.api.dto.PartyParticipantsResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party", description = "파티 API")
interface ParticipantApi {
    @Operation(
        summary = "파티 참여자 목록 조회",
        description = "실시간 파티 진행 기본화면용. 입장 순서로 정렬된 참여자 목록을 반환한다. RealtimeParty 전용, 참여자만 조회 가능.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "참여자 목록 조회 성공")
    @AuthErrorResponses
    @ForbiddenResponse
    @InternalServerErrorResponse
    fun getPartyParticipants(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "파티 ID", example = "1") partyId: Long,
    ): ApiResponse<PartyParticipantsResponse>
}
```

> `ForbiddenResponse` 가 단일 어노테이션이 아니라 메서드명이라면 생략하고 `AuthErrorResponses` 만 유지해도 무방. (Step 3 컴파일에서 확인되면 그대로, 컴파일 실패면 import 제거 + 해당 줄 삭제.)

- [ ] **Step 2: 컴파일 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL (또는 `ForbiddenResponse` 미존재 시 import 와 어노테이션 제거 후 재시도)

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/api/ParticipantApi.kt
git commit -m "feat: ParticipantApi Swagger 인터페이스 추가"
```

---

## Task 10: ParticipantController + 통합 테스트

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/api/ParticipantController.kt`
- Create: `src/test/kotlin/com/team2/server/party/api/ParticipantControllerTest.kt`

- [ ] **Step 1: Controller 통합 테스트 작성**

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.entity.Image
import com.team2.server.common.entity.ImageTargetType
import com.team2.server.common.repository.ImageRepository
import com.team2.server.config.TestcontainersConfiguration
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.entity.RealtimeParty
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.repository.ParticipantRepository
import com.team2.server.party.repository.PartyRepository
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import com.team2.server.user.repository.UserRepository
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class ParticipantControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val partyRepository: PartyRepository,
        private val participantRepository: ParticipantRepository,
        private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
        private val characterRepository: CharacterRepository,
        private val imageRepository: ImageRepository,
        private val userRepository: UserRepository,
        private val jwtTokenProvider: JwtTokenProvider,
        private val jwtProperties: JwtProperties,
    ) {
        private lateinit var owner: User
        private lateinit var member: User
        private lateinit var outsider: User
        private lateinit var realtimeParty: RealtimeParty
        private lateinit var paperOnlyParty: PaperOnlyParty

        @BeforeEach
        fun setUp() {
            realtimeParticipantProfileRepository.deleteAll()
            participantRepository.deleteAll()
            partyRepository.deleteAll()
            imageRepository.deleteAll()
            characterRepository.deleteAll()
            userRepository.deleteAll()

            owner = userRepository.save(User(provider = AuthProvider.GOOGLE, providerId = "o", email = "o@e", name = "owner"))
            member = userRepository.save(User(provider = AuthProvider.GOOGLE, providerId = "m", email = "m@e", name = "member"))
            outsider = userRepository.save(User(provider = AuthProvider.GOOGLE, providerId = "x", email = "x@e", name = "outsider"))

            realtimeParty =
                partyRepository.save(
                    RealtimeParty(
                        ownerId = owner.id,
                        celebrantNickname = "주최자",
                        startedAt = LocalDateTime.now().plusHours(1),
                    ),
                )
            paperOnlyParty =
                partyRepository.save(
                    PaperOnlyParty(
                        ownerId = owner.id,
                        celebrantNickname = "롤링",
                        startedAt = LocalDateTime.now().plusDays(1),
                    ),
                )

            val character = characterRepository.save(Character(name = "octopus"))
            imageRepository.save(
                Image(
                    imageUrl = "https://cdn/char.png",
                    targetType = ImageTargetType.CHARACTER,
                    targetId = character.id,
                    sortOrder = 0,
                ),
            )

            val ownerParticipant =
                participantRepository.save(Participant(party = realtimeParty, user = owner, isCelebrant = true))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = ownerParticipant, nickname = "주최자", character = character),
            )
            val memberParticipant =
                participantRepository.save(Participant(party = realtimeParty, user = member, isCelebrant = false))
            realtimeParticipantProfileRepository.save(
                RealtimeParticipantProfile(participant = memberParticipant, nickname = "참가자A", character = character),
            )
        }

        @Test
        fun `주최자 호출 시 200 과 입장 순서대로 정렬된 목록을 반환한다`() {
            mockMvc.get("/api/v1/parties/${realtimeParty.id}/participants") {
                header(HttpHeaders.AUTHORIZATION, bearer(owner.id))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.totalCount") { value(2) }
                jsonPath("$.data.maxCount") { value(14) }
                jsonPath("$.data.participants", hasSize<Any>(2))
                jsonPath("$.data.participants[0].joinOrder") { value(1) }
                jsonPath("$.data.participants[0].isOwner") { value(true) }
                jsonPath("$.data.participants[0].isCelebrant") { value(true) }
                jsonPath("$.data.participants[0].isMe") { value(true) }
                jsonPath("$.data.participants[0].characterImageUrl") { value("https://cdn/char.png") }
                jsonPath("$.data.participants[1].joinOrder") { value(2) }
                jsonPath("$.data.participants[1].isOwner") { value(false) }
                jsonPath("$.data.participants[1].isMe") { value(false) }
            }
        }

        @Test
        fun `일반 참가자 호출 시 본인의 isMe 만 true`() {
            mockMvc.get("/api/v1/parties/${realtimeParty.id}/participants") {
                header(HttpHeaders.AUTHORIZATION, bearer(member.id))
            }.andExpect {
                status { isOk() }
                jsonPath("$.data.participants[0].isMe") { value(false) }
                jsonPath("$.data.participants[1].isMe") { value(true) }
            }
        }

        @Test
        fun `비참가자 호출 시 403 PARTY_FORBIDDEN`() {
            mockMvc.get("/api/v1/parties/${realtimeParty.id}/participants") {
                header(HttpHeaders.AUTHORIZATION, bearer(outsider.id))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.error.code") { value("PARTY_FORBIDDEN") }
            }
        }

        @Test
        fun `PaperOnly 파티 호출 시 400 PARTY_NOT_REALTIME`() {
            participantRepository.save(Participant(party = paperOnlyParty, user = owner, isCelebrant = true))

            mockMvc.get("/api/v1/parties/${paperOnlyParty.id}/participants") {
                header(HttpHeaders.AUTHORIZATION, bearer(owner.id))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value("PARTY_NOT_REALTIME") }
            }
        }

        @Test
        fun `존재하지 않는 파티 호출 시 404 PARTY_NOT_FOUND`() {
            mockMvc.get("/api/v1/parties/99999/participants") {
                header(HttpHeaders.AUTHORIZATION, bearer(owner.id))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
            }
        }

        @Test
        fun `Authorization 헤더 없으면 401`() {
            mockMvc.get("/api/v1/parties/${realtimeParty.id}/participants")
                .andExpect {
                    status { isUnauthorized() }
                }
        }

        private fun bearer(userId: Long): String = "Bearer ${jwtTokenProvider.createAccessToken(userId)}"
    }
```

> Bearer 토큰 발급 헬퍼 시그니처는 develop 의 기존 테스트(예: `ArchiveControllerTest`) 의 패턴을 그대로 따라야 한다. `jwtTokenProvider.createAccessToken(userId)` 가 없는 시그니처라면 같은 파일 패턴 (`createAccessToken(userId.toString(), ...)` 등) 으로 맞춰서 수정한다.

- [ ] **Step 2: 테스트 실행으로 실패 확인**

```bash
DOCKER_HOST=unix:///Users/choetaegyu/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew test --tests "com.team2.server.party.api.ParticipantControllerTest"
```

Expected: 컴파일 실패 — `ParticipantController` 미존재

- [ ] **Step 3: Controller 구현**

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.api.dto.PartyParticipantsResponse
import com.team2.server.party.application.usecase.GetPartyParticipantsUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class ParticipantController(
    private val getPartyParticipantsUseCase: GetPartyParticipantsUseCase,
) : ParticipantApi {
    @GetMapping("/{partyId}/participants")
    override fun getPartyParticipants(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<PartyParticipantsResponse> {
        val result = getPartyParticipantsUseCase.invoke(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(PartyParticipantsResponse.from(result))
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

```bash
DOCKER_HOST=unix:///Users/choetaegyu/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew test --tests "com.team2.server.party.api.ParticipantControllerTest"
```

Expected: BUILD SUCCESSFUL, 6 tests passed

> 실패 시 가장 흔한 원인: (a) `bearer()` 토큰 시그니처 불일치 — `ArchiveControllerTest` 의 동일 패턴 확인 후 정렬, (b) `setUp` 의 deleteAll 순서로 FK 충돌 — 자식부터 부모 순(profile → participant → party → user/character) 으로 정렬.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/api/ParticipantController.kt \
        src/test/kotlin/com/team2/server/party/api/ParticipantControllerTest.kt
git commit -m "feat: 파티 참여자 목록 조회 Controller 추가"
```

---

## Task 11: 전체 검증 + Push + PR 업데이트

**Files:** 변경 없음

- [ ] **Step 1: ktlint 검증**

```bash
./gradlew ktlintCheck
```

Expected: BUILD SUCCESSFUL. 실패하면 `./gradlew ktlintFormat` 실행 후 변경분 커밋 (`chore: ktlint 포맷 정렬`).

- [ ] **Step 2: 전체 build + test**

```bash
DOCKER_HOST=unix:///Users/choetaegyu/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew build
```

Expected: BUILD SUCCESSFUL, all tests passed (실패 0, 스킵 0).

- [ ] **Step 3: 원격 push**

```bash
git push origin feature/party-participants-list
```

Expected: 정상 push. PR #149 가 자동으로 새 커밋들 반영.

- [ ] **Step 4: PR 본문 갱신**

PR #149 의 description 에 다음 추가 (또는 새 PR comment):

```
구현 완료 (TDD 11 task).
- Repository, Service, UseCase 단위·통합 테스트 모두 통과.
- 응답 스키마는 spec 문서 그대로.
- 패키지 경로는 develop 의 옛 레이아웃 + Archive(#127) 패턴을 따름.
```

- [ ] **Step 5: 검증 완료**

CI 그린 확인 후 리뷰 요청.
