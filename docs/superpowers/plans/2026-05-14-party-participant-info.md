# Party Participant Info (Realtime Profile) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원이 실시간 파티 입장 화면에서 닉네임/캐릭터를 작성·수정할 수 있는 GET/PUT API 두 개를 구현한다. 주최자는 닉네임 변경이 불가하다.

**Architecture:** 기존 party 패키지 레이어드 구조(Controller → UseCase → Service → Repository/Domain)를 따른다. `RealtimeParticipantProfile` aggregate는 새 `RealtimeParticipantProfileService`가 책임지고, participant 생성·복원은 기존 `ParticipantService.joinMember`를 재사용한다. UseCase 두 개(`GetMyRealtimeProfileUseCase`, `UpsertMyRealtimeProfileUseCase`)가 각각 `@Transactional` 경계를 잡는다.

**Tech Stack:** Kotlin + Spring Boot + JPA(Hibernate) + Bean Validation + Spring MVC + JUnit 5 + Mockito + MockMvc.

**Spec:** `docs/superpowers/specs/2026-05-14-party-participant-info-design.md`

---

## File Map

신규 파일:

- `src/main/kotlin/com/team2/server/party/dto/UpsertParticipantRealtimeProfileRequest.kt`
- `src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResponse.kt`
- `src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResult.kt`
- `src/main/kotlin/com/team2/server/party/service/RealtimeParticipantProfileService.kt`
- `src/main/kotlin/com/team2/server/party/usecase/GetMyRealtimeProfileUseCase.kt`
- `src/main/kotlin/com/team2/server/party/usecase/UpsertMyRealtimeProfileUseCase.kt`
- `src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileApi.kt`
- `src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileController.kt`
- `src/test/kotlin/com/team2/server/party/service/RealtimeParticipantProfileServiceTest.kt`
- `src/test/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileControllerTest.kt`

수정 파일:

- `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt` — `PARTY_NOT_REALTIME`, `PARTY_HOST_NICKNAME_NOT_EDITABLE` 추가
- `src/main/kotlin/com/team2/server/party/repository/PartyInviteRepository.kt` (수정 없음 예상, 기존 `findByToken` 사용)

ArchUnit/구조 규칙은 기존 규칙이 새 클래스를 자동 검증한다. 추가 ArchUnit 규칙은 없다.

---

## Task 1: ErrorCode 추가

**Files:**

- Modify: `src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt`

- [ ] **Step 1: ErrorCode enum 두 개 추가**

`ErrorCode.kt`의 `ROLLING_PAPER_NOT_FOUND(...)` 라인 뒤에 두 개 항목 추가. enum 마지막 항목의 콤마/세미콜론 처리에 주의.

수정 전 마지막 항목 부근:

```kotlin
    ROLLING_PAPER_NOT_VIEWABLE(HttpStatus.FORBIDDEN, "아직 롤링페이퍼를 확인할 수 없습니다"),
    ROLLING_PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "롤링페이퍼를 찾을 수 없습니다"),
}
```

수정 후:

```kotlin
    ROLLING_PAPER_NOT_VIEWABLE(HttpStatus.FORBIDDEN, "아직 롤링페이퍼를 확인할 수 없습니다"),
    ROLLING_PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "롤링페이퍼를 찾을 수 없습니다"),
    PARTY_NOT_REALTIME(HttpStatus.BAD_REQUEST, "실시간 파티가 아닙니다"),
    PARTY_HOST_NICKNAME_NOT_EDITABLE(HttpStatus.BAD_REQUEST, "주최자 닉네임은 변경할 수 없습니다"),
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/common/exception/ErrorCode.kt
git commit -m "feat: 실시간 프로필용 ErrorCode 두 종 추가"
```

---

## Task 2: Request/Response DTO 추가

**Files:**

- Create: `src/main/kotlin/com/team2/server/party/dto/UpsertParticipantRealtimeProfileRequest.kt`
- Create: `src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResult.kt`
- Create: `src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResponse.kt`

- [ ] **Step 1: Request DTO 작성**

`src/main/kotlin/com/team2/server/party/dto/UpsertParticipantRealtimeProfileRequest.kt`:

```kotlin
package com.team2.server.party.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "실시간 파티 입장 프로필 작성·수정 요청")
data class UpsertParticipantRealtimeProfileRequest(
    @field:NotBlank
    @field:Size(max = 10)
    @Schema(description = "파티 내 표시 닉네임. 최대 10자. trim된 값이 저장됩니다.", example = "안녕용가리")
    val nickname: String,
    @field:NotNull
    @Schema(description = "선택한 캐릭터 ID", example = "1")
    val characterId: Long?,
)
```

`characterId` 타입을 `Long?`로 둔 이유:

- `@NotNull` 검증으로 누락 요청을 명시적으로 400 처리하기 위함.
- non-null `Long`이면 Jackson이 0L 같은 기본값을 silently 채워 검증이 우회될 수 있다.

- [ ] **Step 2: Result DTO 작성**

`src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResult.kt`:

```kotlin
package com.team2.server.party.dto

data class ParticipantRealtimeProfileResult(
    val participantId: Long,
    val isHost: Boolean,
    val nickname: String?,
    val character: CharacterResult?,
) {
    val nicknameEditable: Boolean get() = !isHost
}
```

UseCase 내부 결과 DTO. Controller 응답으로 매핑된다.

- [ ] **Step 3: Response DTO 작성**

`src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResponse.kt`:

```kotlin
package com.team2.server.party.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "실시간 파티 입장 프로필 응답")
data class ParticipantRealtimeProfileResponse(
    @Schema(description = "회원 participant ID", example = "1")
    val participantId: Long,
    @Schema(description = "주최자 여부", example = "false")
    val isHost: Boolean,
    @Schema(description = "현재 저장된 닉네임. 미설정이면 null", example = "안녕용가리", nullable = true)
    val nickname: String?,
    @Schema(description = "닉네임 수정 가능 여부. 주최자는 false", example = "true")
    val nicknameEditable: Boolean,
    @Schema(description = "선택된 캐릭터. 미선택이면 null", nullable = true)
    val character: CharacterResponse?,
) {
    companion object {
        fun from(result: ParticipantRealtimeProfileResult): ParticipantRealtimeProfileResponse =
            ParticipantRealtimeProfileResponse(
                participantId = result.participantId,
                isHost = result.isHost,
                nickname = result.nickname,
                nicknameEditable = result.nicknameEditable,
                character = result.character?.let { CharacterResponse.from(it) },
            )
    }
}
```

- [ ] **Step 4: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/dto/UpsertParticipantRealtimeProfileRequest.kt \
        src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResult.kt \
        src/main/kotlin/com/team2/server/party/dto/ParticipantRealtimeProfileResponse.kt
git commit -m "feat: 실시간 프로필 입장 API DTO 추가"
```

---

## Task 3: RealtimeParticipantProfileService — failing tests 먼저

**Files:**

- Create: `src/test/kotlin/com/team2/server/party/service/RealtimeParticipantProfileServiceTest.kt`

이 Task는 RED. 다음 Task에서 GREEN.

- [ ] **Step 1: 실패 테스트 작성**

`src/test/kotlin/com/team2/server/party/service/RealtimeParticipantProfileServiceTest.kt`:

```kotlin
package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertSame

@ExtendWith(MockitoExtension::class)
class RealtimeParticipantProfileServiceTest {
    @Mock
    lateinit var profileRepository: RealtimeParticipantProfileRepository

    @InjectMocks
    lateinit var service: RealtimeParticipantProfileService

    private val party =
        PaperOnlyParty(
            ownerId = 1L,
            celebrantNickname = "홍길동",
            startedAt = java.time.LocalDateTime.now(),
        )
    private val character = Character(name = "기본")
    private val anotherCharacter = Character(name = "리본")

    private fun newParticipant(isCelebrant: Boolean): Participant = Participant(party = party, isCelebrant = isCelebrant)

    @Test
    fun `기존 프로필이 없으면 새로 생성한다`() {
        val participant = newParticipant(isCelebrant = false)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(null)
        whenever(profileRepository.save(any<RealtimeParticipantProfile>())).thenAnswer { it.arguments[0] }

        val result = service.upsert(participant, "안녕", character, isHostNicknameLocked = false)

        val captor = argumentCaptor<RealtimeParticipantProfile>()
        verify(profileRepository).save(captor.capture())
        val saved = captor.firstValue
        assertEquals("안녕", saved.nickname)
        assertSame(character, saved.character)
        assertSame(participant, saved.participant)
        assertEquals("안녕", result.nickname)
    }

    @Test
    fun `기존 프로필이 있으면 nickname과 character를 갱신한다 (locked = false)`() {
        val participant = newParticipant(isCelebrant = false)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "old", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)

        val result = service.upsert(participant, "new", anotherCharacter, isHostNicknameLocked = false)

        verify(profileRepository, never()).save(any<RealtimeParticipantProfile>())
        assertEquals("new", existing.nickname)
        assertSame(anotherCharacter, existing.character)
        assertSame(existing, result)
    }

    @Test
    fun `locked = true이고 nickname이 같으면 character만 갱신한다`() {
        val participant = newParticipant(isCelebrant = true)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "host", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)

        val result = service.upsert(participant, "host", anotherCharacter, isHostNicknameLocked = true)

        verify(profileRepository, never()).save(any<RealtimeParticipantProfile>())
        assertEquals("host", existing.nickname)
        assertSame(anotherCharacter, existing.character)
        assertSame(existing, result)
    }

    @Test
    fun `locked = true이고 nickname이 다르면 PARTY_HOST_NICKNAME_NOT_EDITABLE`() {
        val participant = newParticipant(isCelebrant = true)
        val existing =
            RealtimeParticipantProfile(participant = participant, nickname = "host", character = character)
        whenever(profileRepository.findByParticipant(participant)).thenReturn(existing)

        val e =
            assertThrows<BusinessException> {
                service.upsert(participant, "different", anotherCharacter, isHostNicknameLocked = true)
            }
        assertEquals(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE, e.errorCode)
        assertEquals("host", existing.nickname)
        assertSame(character, existing.character)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

```bash
./gradlew test --tests "com.team2.server.party.service.RealtimeParticipantProfileServiceTest"
```

Expected: 컴파일 실패. `RealtimeParticipantProfileService` 클래스가 없어서 unresolved reference.

- [ ] **Step 3: 커밋**

```bash
git add src/test/kotlin/com/team2/server/party/service/RealtimeParticipantProfileServiceTest.kt
git commit -m "test: RealtimeParticipantProfileService 실패 테스트 추가"
```

---

## Task 4: RealtimeParticipantProfileService — GREEN

**Files:**

- Create: `src/main/kotlin/com/team2/server/party/service/RealtimeParticipantProfileService.kt`

- [ ] **Step 1: Service 구현**

`src/main/kotlin/com/team2/server/party/service/RealtimeParticipantProfileService.kt`:

```kotlin
package com.team2.server.party.service

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.RealtimeParticipantProfile
import com.team2.server.party.repository.RealtimeParticipantProfileRepository
import org.springframework.stereotype.Service

@Service
class RealtimeParticipantProfileService(
    private val profileRepository: RealtimeParticipantProfileRepository,
) {
    fun findByParticipant(participant: Participant): RealtimeParticipantProfile? =
        profileRepository.findByParticipant(participant)

    fun upsert(
        participant: Participant,
        nickname: String,
        character: Character,
        isHostNicknameLocked: Boolean,
    ): RealtimeParticipantProfile {
        val existing = profileRepository.findByParticipant(participant)
        if (existing == null) {
            return profileRepository.save(
                RealtimeParticipantProfile(
                    participant = participant,
                    nickname = nickname,
                    character = character,
                ),
            )
        }
        if (isHostNicknameLocked && existing.nickname != nickname) {
            throw BusinessException(ErrorCode.PARTY_HOST_NICKNAME_NOT_EDITABLE)
        }
        existing.nickname = nickname
        existing.character = character
        return existing
    }
}
```

레이어드 규칙 확인:

- 1 Aggregate(= `RealtimeParticipantProfile`) = 1 Service. ✓
- `@Transactional` 선언 없음 (UseCase에서 잡음). ✓
- 다른 Service 호출 없음. ✓
- 도메인 객체 반환 (DTO 변환 없음). ✓
- 의존성 1개. ✓

- [ ] **Step 2: 테스트 통과 확인**

```bash
./gradlew test --tests "com.team2.server.party.service.RealtimeParticipantProfileServiceTest"
```

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/service/RealtimeParticipantProfileService.kt
git commit -m "feat: RealtimeParticipantProfileService 구현"
```

---

## Task 5: GetMyRealtimeProfileUseCase

**Files:**

- Create: `src/main/kotlin/com/team2/server/party/usecase/GetMyRealtimeProfileUseCase.kt`

(Controller 통합 테스트에서 검증하므로 별도 UseCase 단위 테스트는 만들지 않는다. Task 9의 통합 테스트가 GET 시나리오를 커버한다.)

- [ ] **Step 1: UseCase 구현**

`src/main/kotlin/com/team2/server/party/usecase/GetMyRealtimeProfileUseCase.kt`:

```kotlin
package com.team2.server.party.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.dto.CharacterResult
import com.team2.server.party.dto.ParticipantRealtimeProfileResult
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.PartyInviteRepository
import com.team2.server.party.service.ParticipantService
import com.team2.server.party.service.PartyInviteService
import com.team2.server.party.service.RealtimeParticipantProfileService
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class GetMyRealtimeProfileUseCase(
    private val partyInviteService: PartyInviteService,
    private val participantService: ParticipantService,
    private val profileService: RealtimeParticipantProfileService,
    private val userRepository: UserRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
) {
    @Transactional
    fun invoke(
        inviteToken: String,
        userId: Long,
    ): ParticipantRealtimeProfileResult {
        val now = LocalDateTime.now()
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val party = invite.party
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(party, user)
        val profile = profileService.findByParticipant(participant)
        val character =
            profile?.character?.let {
                CharacterResult(
                    characterId = it.id,
                    name = it.name,
                    characterImageUrl = characterImageUrlResolver.resolve(it),
                    characterThumbnailImageUrl = null,
                )
            }
        return ParticipantRealtimeProfileResult(
            participantId = participant.id,
            isHost = participant.isCelebrant,
            nickname = profile?.nickname,
            character = character,
        )
    }
}
```

설계 메모:

- `CharacterResult.characterThumbnailImageUrl`은 입장 프로필 응답에서 사용하지 않는다. `null`로 둔다. 기존 `CharacterResponse`는 이 필드를 nullable로 갖고 있어 그대로 호환된다.
- `partyInviteService.findUsableInvite`는 토큰 미존재 → `PARTY_NOT_FOUND`, 만료 → `INVITE_LINK_EXPIRED`를 던지는 기존 메서드다(`JoinPartyInviteUseCase` 참조).
- `@Transactional`은 participant fallback 생성/복원이 일어날 수 있으므로 readOnly가 아님.

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/usecase/GetMyRealtimeProfileUseCase.kt
git commit -m "feat: 실시간 프로필 조회 UseCase 구현"
```

---

## Task 6: UpsertMyRealtimeProfileUseCase

**Files:**

- Create: `src/main/kotlin/com/team2/server/party/usecase/UpsertMyRealtimeProfileUseCase.kt`

- [ ] **Step 1: UseCase 구현**

`src/main/kotlin/com/team2/server/party/usecase/UpsertMyRealtimeProfileUseCase.kt`:

```kotlin
package com.team2.server.party.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.dto.CharacterImageUrlResolver
import com.team2.server.party.dto.CharacterResult
import com.team2.server.party.dto.ParticipantRealtimeProfileResult
import com.team2.server.party.dto.UpsertParticipantRealtimeProfileRequest
import com.team2.server.party.entity.PartyOption
import com.team2.server.party.repository.CharacterRepository
import com.team2.server.party.service.ParticipantService
import com.team2.server.party.service.PartyInviteService
import com.team2.server.party.service.RealtimeParticipantProfileService
import com.team2.server.user.repository.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class UpsertMyRealtimeProfileUseCase(
    private val partyInviteService: PartyInviteService,
    private val participantService: ParticipantService,
    private val profileService: RealtimeParticipantProfileService,
    private val characterRepository: CharacterRepository,
    private val userRepository: UserRepository,
    private val characterImageUrlResolver: CharacterImageUrlResolver,
) {
    @Transactional
    fun invoke(
        inviteToken: String,
        userId: Long,
        request: UpsertParticipantRealtimeProfileRequest,
    ): ParticipantRealtimeProfileResult {
        val now = LocalDateTime.now()
        val invite = partyInviteService.findUsableInvite(inviteToken, now)
        val party = invite.party
        if (party.isEnded(now)) {
            throw BusinessException(ErrorCode.PARTY_ENDED)
        }
        if (party.partyOption != PartyOption.REALTIME) {
            throw BusinessException(ErrorCode.PARTY_NOT_REALTIME)
        }
        val characterId = request.characterId
            ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        val character =
            characterRepository.findById(characterId).orElseThrow {
                BusinessException(ErrorCode.CHARACTER_NOT_FOUND)
            }
        val user =
            userRepository.findByIdOrNull(userId)
                ?: throw BusinessException(ErrorCode.AUTH_USER_NOT_FOUND)
        val participant = participantService.joinMember(party, user)
        val nickname = request.nickname.trim()
        if (nickname.isBlank()) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }
        val profile =
            profileService.upsert(
                participant = participant,
                nickname = nickname,
                character = character,
                isHostNicknameLocked = participant.isCelebrant,
            )
        return ParticipantRealtimeProfileResult(
            participantId = participant.id,
            isHost = participant.isCelebrant,
            nickname = profile.nickname,
            character =
                CharacterResult(
                    characterId = profile.character?.id ?: character.id,
                    name = profile.character?.name ?: character.name,
                    characterImageUrl = characterImageUrlResolver.resolve(profile.character ?: character),
                    characterThumbnailImageUrl = null,
                ),
        )
    }
}
```

설계 메모:

- request validation은 Controller에서 `@Valid`로 1차 검증되지만, UseCase는 `characterId == null`에 대한 방어를 추가로 둔다 (UseCase 단독 호출 가능성을 고려한 방어, `INVALID_INPUT`으로 던짐).
- `nickname.trim()`은 rolling paper 작성 정책과 동일. `@Size`는 trim 전 길이 기준이므로 trim된 값이 빈 문자열이 될 수 있다(예: "          ", 10개 공백). 이 경우 `RealtimeParticipantProfile.nickname`이 빈 문자열로 저장되는 것을 막기 위해 trim 후 `isBlank()` 가드를 추가한다(`INVALID_INPUT`).

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/usecase/UpsertMyRealtimeProfileUseCase.kt
git commit -m "feat: 실시간 프로필 작성·수정 UseCase 구현"
```

---

## Task 7: Controller Swagger API interface

**Files:**

- Create: `src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileApi.kt`

- [ ] **Step 1: Swagger API interface 작성**

`src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileApi.kt`:

```kotlin
package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.common.response.ErrorResponse
import com.team2.server.common.swagger.AuthErrorResponses
import com.team2.server.common.swagger.InternalServerErrorResponse
import com.team2.server.party.dto.ParticipantRealtimeProfileResponse
import com.team2.server.party.dto.UpsertParticipantRealtimeProfileRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(name = "Party Invite", description = "파티 초대장 API")
interface ParticipantRealtimeProfileApi {
    @Operation(
        summary = "실시간 파티 입장 프로필 조회",
        description =
            "회원이 실시간 파티에 입장하기 전에 자신의 닉네임/캐릭터 상태를 조회한다. " +
                "회원 participant가 없으면 생성한다. 만료된 토큰 또는 종료된 파티에는 실패한다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "프로필 조회 성공")
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "400",
        description = "초대 링크 만료, 파티 종료, 비-실시간 파티",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "비-실시간 파티",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "PARTY_NOT_REALTIME",
                                "message": "실시간 파티가 아닙니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 초대 토큰",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @InternalServerErrorResponse
    fun getMyRealtimeProfile(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
    ): ApiResponse<ParticipantRealtimeProfileResponse>

    @Operation(
        summary = "실시간 파티 입장 프로필 작성·수정",
        description =
            "회원의 실시간 파티 입장 프로필을 작성하거나 수정한다. 주최자는 닉네임을 변경할 수 없다 " +
                "(같은 값 재전송은 허용). 캐릭터는 모든 참가자가 변경 가능하다.",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(responseCode = "200", description = "프로필 작성·수정 성공")
    @AuthErrorResponses
    @SwaggerApiResponse(
        responseCode = "400",
        description = "검증 실패, 초대 링크 만료, 파티 종료, 비-실시간 파티, 주최자 닉네임 변경 시도",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
                examples = [
                    ExampleObject(
                        name = "주최자 닉네임 변경 불가",
                        value = """
                            {
                              "status": 400,
                              "error": {
                                "code": "PARTY_HOST_NICKNAME_NOT_EDITABLE",
                                "message": "주최자 닉네임은 변경할 수 없습니다"
                              }
                            }
                        """,
                    ),
                ],
            ),
        ],
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "존재하지 않는 초대 토큰 또는 캐릭터",
        content = [
            Content(
                mediaType = "application/json",
                schema = Schema(implementation = ErrorResponse::class),
            ),
        ],
    )
    @InternalServerErrorResponse
    fun upsertMyRealtimeProfile(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "초대 토큰", example = "exampletoken0000") inviteToken: String,
        request: UpsertParticipantRealtimeProfileRequest,
    ): ApiResponse<ParticipantRealtimeProfileResponse>
}
```

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileApi.kt
git commit -m "feat: 실시간 프로필 Swagger API 인터페이스 추가"
```

---

## Task 8: Controller 구현

**Files:**

- Create: `src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileController.kt`

- [ ] **Step 1: Controller 작성**

`src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileController.kt`:

```kotlin
package com.team2.server.party.controller

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.response.ApiResponse
import com.team2.server.party.dto.ParticipantRealtimeProfileResponse
import com.team2.server.party.dto.UpsertParticipantRealtimeProfileRequest
import com.team2.server.party.usecase.GetMyRealtimeProfileUseCase
import com.team2.server.party.usecase.UpsertMyRealtimeProfileUseCase
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/party-invites")
class ParticipantRealtimeProfileController(
    private val getMyRealtimeProfileUseCase: GetMyRealtimeProfileUseCase,
    private val upsertMyRealtimeProfileUseCase: UpsertMyRealtimeProfileUseCase,
) : ParticipantRealtimeProfileApi {
    @GetMapping("/{inviteToken}/participants/me/realtime-profile")
    override fun getMyRealtimeProfile(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
    ): ApiResponse<ParticipantRealtimeProfileResponse> {
        val result = getMyRealtimeProfileUseCase.invoke(inviteToken, principal.userId)
        return ApiResponse.success(ParticipantRealtimeProfileResponse.from(result))
    }

    @PutMapping("/{inviteToken}/participants/me/realtime-profile")
    override fun upsertMyRealtimeProfile(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
        @Valid @RequestBody request: UpsertParticipantRealtimeProfileRequest,
    ): ApiResponse<ParticipantRealtimeProfileResponse> {
        val result = upsertMyRealtimeProfileUseCase.invoke(inviteToken, principal.userId, request)
        return ApiResponse.success(ParticipantRealtimeProfileResponse.from(result))
    }
}
```

레이어드 규칙:

- Controller → UseCase만 의존. Repository/Domain 직접 의존 없음. ✓
- DTO 변환 책임은 UseCase가 `Result`로 내려주고 Controller가 `Response.from()`로 위임. UseCase가 직접 Response 변환을 안 하므로 UseCase 줄 수 절감. (rolling paper와 동일 패턴)

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋**

```bash
git add src/main/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileController.kt
git commit -m "feat: 실시간 프로필 Controller 구현"
```

---

## Task 9: Controller 통합 테스트

**Files:**

- Create: `src/test/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileControllerTest.kt`

이 통합 테스트가 GET/PUT 시나리오 전체를 검증한다(spec 10절의 테스트 계획 전체).

- [ ] **Step 1: 테스트 클래스 골격 작성**

`src/test/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileControllerTest.kt`:

```kotlin
package com.team2.server.party.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.team2.server.auth.config.JwtProperties
import com.team2.server.auth.jwt.JwtTokenProvider
import com.team2.server.common.DatabaseCleanup
import com.team2.server.party.entity.Character
import com.team2.server.party.entity.PaperOnlyParty
import com.team2.server.party.entity.Participant
import com.team2.server.party.entity.Party
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
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
class ParticipantRealtimeProfileControllerTest
    @Autowired
    constructor(
        private val mockMvc: MockMvc,
        private val objectMapper: ObjectMapper,
        private val partyRepository: PartyRepository,
        private val partyInviteRepository: PartyInviteRepository,
        private val participantRepository: ParticipantRepository,
        private val profileRepository: RealtimeParticipantProfileRepository,
        private val characterRepository: CharacterRepository,
        private val userRepository: UserRepository,
        private val databaseCleanup: DatabaseCleanup,
        private val jwtProperties: JwtProperties,
    ) {
        private val tokenProvider = JwtTokenProvider(jwtProperties)

        @BeforeEach
        fun setUp() {
            databaseCleanup.execute()
        }

        // tests follow

        private fun saveUser(
            providerId: String,
            email: String,
        ): User =
            userRepository.saveAndFlush(
                User(
                    name = "tester-$providerId",
                    birthDay = "01-01",
                    provider = AuthProvider.KAKAO,
                    providerId = providerId,
                    email = email,
                ),
            )

        private fun saveParty(party: Party): Party = partyRepository.saveAndFlush(party)

        private fun saveInvite(
            party: Party,
            token: String,
            expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        ): PartyInvite =
            partyInviteRepository.saveAndFlush(
                PartyInvite(party = party, token = token, expiresAt = expiresAt),
            )

        private fun saveCharacter(name: String): Character = characterRepository.saveAndFlush(Character(name = name))
    }
```

`User` 생성자 시그니처는 다른 테스트의 `saveUser` 호출 부분과 동일하게 맞춘다. 만약 다른 테스트(`PartyInviteLookupControllerTest.saveUser`)가 다른 시그니처를 쓰면 그쪽 코드를 따라간다.

- [ ] **Step 2: 빌드 확인**

```bash
./gradlew compileTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: 커밋 (테스트 골격)**

```bash
git add src/test/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileControllerTest.kt
git commit -m "test: 실시간 프로필 Controller 테스트 골격 추가"
```

- [ ] **Step 4: GET 테스트 추가 — 회원 첫 진입 (프로필 없음)**

`// tests follow` 자리에 첫 테스트를 삽입한다:

```kotlin
        @Test
        fun `GET 회원 첫 진입은 participant를 생성하고 nickname null character null로 응답한다`() {
            val user = saveUser("kakao-get-first", "first@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "getfirst0000001")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getfirst0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participantId") { exists() }
                    jsonPath("$.data.isHost") { value(false) }
                    jsonPath("$.data.nickname") { value(nullValue()) }
                    jsonPath("$.data.nicknameEditable") { value(true) }
                    jsonPath("$.data.character") { value(nullValue()) }
                }

            val participant = assertNotNull(participantRepository.findByPartyAndUser(party, user))
            assertEquals(false, participant.isCelebrant)
            assertNull(profileRepository.findByParticipant(participant))
        }
```

- [ ] **Step 5: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 1 test passed.

- [ ] **Step 6: GET 테스트 추가 — 주최자 진입 (프로필 prefill)**

```kotlin
        @Test
        fun `GET 주최자 진입은 isHost true, nicknameEditable false, prefilled nickname과 character`() {
            val host = saveUser("kakao-get-host", "host@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = host.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = host, isCelebrant = true),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "홍길동", character = character),
            )
            saveInvite(party, "gethost00000001")
            val accessToken = tokenProvider.issue(host)

            mockMvc
                .get("/api/v1/party-invites/gethost00000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.participantId") { value(participant.id) }
                    jsonPath("$.data.isHost") { value(true) }
                    jsonPath("$.data.nickname") { value("홍길동") }
                    jsonPath("$.data.nicknameEditable") { value(false) }
                    jsonPath("$.data.character.characterId") { value(character.id) }
                    jsonPath("$.data.character.name") { value("기본") }
                }
        }
```

- [ ] **Step 7: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 2 tests passed.

- [ ] **Step 8: GET 테스트 추가 — 만료/종료/비실시간/없는토큰/401**

```kotlin
        @Test
        fun `GET 만료된 초대 토큰이면 INVITE_LINK_EXPIRED`() {
            val user = saveUser("kakao-get-expired", "getexp@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "getexpired00001", LocalDateTime.now().minusHours(1))
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getexpired00001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVITE_LINK_EXPIRED") }
                }
            assertEquals(0, participantRepository.count())
        }

        @Test
        fun `GET 종료된 파티이면 PARTY_ENDED`() {
            val user = saveUser("kakao-get-ended", "getend@kakao.local")
            val createdAt = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "getended0000001")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getended0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_ENDED") }
                }
        }

        @Test
        fun `GET PAPER_ONLY 파티이면 PARTY_NOT_REALTIME`() {
            val user = saveUser("kakao-get-paper", "getpaper@kakao.local")
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "getpaperonly001")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/getpaperonly001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_NOT_REALTIME") }
                }
        }

        @Test
        fun `GET 존재하지 않는 토큰이면 PARTY_NOT_FOUND`() {
            val user = saveUser("kakao-get-404", "get404@kakao.local")
            val accessToken = tokenProvider.issue(user)

            mockMvc
                .get("/api/v1/party-invites/missingtoken000/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("PARTY_NOT_FOUND") }
                }
        }

        @Test
        fun `GET 인증 없으면 401`() {
            mockMvc
                .get("/api/v1/party-invites/anytoken0000001/participants/me/realtime-profile")
                .andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_UNAUTHORIZED") }
                }
        }
```

- [ ] **Step 9: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 7 tests passed.

- [ ] **Step 10: PUT 테스트 추가 — 첫 작성**

```kotlin
        @Test
        fun `PUT 회원 첫 작성은 프로필을 생성하고 200으로 응답한다`() {
            val user = saveUser("kakao-put-first", "putfirst@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putfirst0000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕용가리", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putfirst0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.isHost") { value(false) }
                    jsonPath("$.data.nickname") { value("안녕용가리") }
                    jsonPath("$.data.nicknameEditable") { value(true) }
                    jsonPath("$.data.character.characterId") { value(character.id) }
                }

            val participant = assertNotNull(participantRepository.findByPartyAndUser(party, user))
            val profile = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("안녕용가리", profile.nickname)
            assertEquals(character.id, profile.character?.id)
        }
```

- [ ] **Step 11: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 8 tests passed.

- [ ] **Step 12: PUT 테스트 추가 — 수정 (참가자, locked 아님)**

```kotlin
        @Test
        fun `PUT 회원 수정은 nickname과 character 모두 갱신한다`() {
            val user = saveUser("kakao-put-update", "putupdate@kakao.local")
            val character1 = saveCharacter("기본")
            val character2 = saveCharacter("리본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = user, isCelebrant = false),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "old", character = character1),
            )
            saveInvite(party, "putupdate000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "new", "characterId" to character2.id)

            mockMvc
                .put("/api/v1/party-invites/putupdate000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.nickname") { value("new") }
                    jsonPath("$.data.character.characterId") { value(character2.id) }
                }

            val updated = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("new", updated.nickname)
            assertEquals(character2.id, updated.character?.id)
        }
```

- [ ] **Step 13: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 9 tests passed.

- [ ] **Step 14: PUT 테스트 추가 — 주최자 닉네임 변경 시도/같은 닉네임 + 캐릭터만 변경**

```kotlin
        @Test
        fun `PUT 주최자가 다른 nickname을 보내면 PARTY_HOST_NICKNAME_NOT_EDITABLE`() {
            val host = saveUser("kakao-put-host-x", "puthostx@kakao.local")
            val character1 = saveCharacter("기본")
            val character2 = saveCharacter("리본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = host.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = host, isCelebrant = true),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "홍길동", character = character1),
            )
            saveInvite(party, "puthostxnick001")
            val accessToken = tokenProvider.issue(host)
            val body = mapOf("nickname" to "다른이름", "characterId" to character2.id)

            mockMvc
                .put("/api/v1/party-invites/puthostxnick001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_HOST_NICKNAME_NOT_EDITABLE") }
                }

            val unchanged = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("홍길동", unchanged.nickname)
            assertEquals(character1.id, unchanged.character?.id)
        }

        @Test
        fun `PUT 주최자가 같은 nickname과 다른 character를 보내면 character만 갱신된다`() {
            val host = saveUser("kakao-put-host-ok", "puthostok@kakao.local")
            val character1 = saveCharacter("기본")
            val character2 = saveCharacter("리본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = host.id,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            val participant =
                participantRepository.saveAndFlush(
                    Participant(party = party, user = host, isCelebrant = true),
                )
            profileRepository.saveAndFlush(
                RealtimeParticipantProfile(participant = participant, nickname = "홍길동", character = character1),
            )
            saveInvite(party, "puthostokchr001")
            val accessToken = tokenProvider.issue(host)
            val body = mapOf("nickname" to "홍길동", "characterId" to character2.id)

            mockMvc
                .put("/api/v1/party-invites/puthostokchr001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.isHost") { value(true) }
                    jsonPath("$.data.nickname") { value("홍길동") }
                    jsonPath("$.data.character.characterId") { value(character2.id) }
                }

            val updated = assertNotNull(profileRepository.findByParticipant(participant))
            assertEquals("홍길동", updated.nickname)
            assertEquals(character2.id, updated.character?.id)
        }
```

- [ ] **Step 15: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 11 tests passed.

- [ ] **Step 16: PUT validation/에러 테스트 추가**

```kotlin
        @Test
        fun `PUT nickname이 blank이면 400`() {
            val user = saveUser("kakao-put-blank", "putblank@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putblank0000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "   ", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putblank0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `PUT nickname이 10자 초과면 400`() {
            val user = saveUser("kakao-put-long", "putlong@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putlong00000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "12345678901", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putlong00000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `PUT characterId가 누락되면 400`() {
            val user = saveUser("kakao-put-noc", "putnoc@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putnoc000000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕")

            mockMvc
                .put("/api/v1/party-invites/putnoc000000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `PUT 없는 characterId면 CHARACTER_NOT_FOUND`() {
            val user = saveUser("kakao-put-nochar", "putnochar@kakao.local")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putnochar000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to 999999L)

            mockMvc
                .put("/api/v1/party-invites/putnochar000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isNotFound() }
                    jsonPath("$.error.code") { value("CHARACTER_NOT_FOUND") }
                }
        }

        @Test
        fun `PUT 만료된 토큰이면 INVITE_LINK_EXPIRED, 데이터 변경 없음`() {
            val user = saveUser("kakao-put-exp", "putexp@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().plusMinutes(10).truncatedTo(ChronoUnit.SECONDS),
                    ),
                )
            saveInvite(party, "putexpired00001", LocalDateTime.now().minusHours(1))
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putexpired00001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("INVITE_LINK_EXPIRED") }
                }

            assertEquals(0, participantRepository.count())
            assertEquals(0, profileRepository.count())
        }

        @Test
        fun `PUT 종료된 파티이면 PARTY_ENDED`() {
            val user = saveUser("kakao-put-end", "putend@kakao.local")
            val character = saveCharacter("기본")
            val createdAt = LocalDateTime.now().minusDays(8).truncatedTo(ChronoUnit.SECONDS)
            val party =
                saveParty(
                    RealtimeParty(
                        ownerId = 999L,
                        celebrantNickname = "홍길동",
                        startedAt = createdAt.toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "putended0000001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putended0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_ENDED") }
                }
        }

        @Test
        fun `PUT PAPER_ONLY 파티이면 PARTY_NOT_REALTIME`() {
            val user = saveUser("kakao-put-pp", "putpp@kakao.local")
            val character = saveCharacter("기본")
            val party =
                saveParty(
                    PaperOnlyParty(
                        ownerId = 1L,
                        celebrantNickname = "홍길동",
                        startedAt = LocalDateTime.now().toLocalDate().atStartOfDay(),
                    ),
                )
            saveInvite(party, "putpaperonly001")
            val accessToken = tokenProvider.issue(user)
            val body = mapOf("nickname" to "안녕", "characterId" to character.id)

            mockMvc
                .put("/api/v1/party-invites/putpaperonly001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer $accessToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value("PARTY_NOT_REALTIME") }
                }
        }

        @Test
        fun `PUT 인증 없으면 401`() {
            val body = mapOf("nickname" to "안녕", "characterId" to 1L)
            mockMvc
                .put("/api/v1/party-invites/anytoken0000001/participants/me/realtime-profile") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_UNAUTHORIZED") }
                }
        }

        @Test
        fun `PUT 잘못된 Bearer 토큰이면 AUTH_INVALID_TOKEN`() {
            val body = mapOf("nickname" to "안녕", "characterId" to 1L)
            mockMvc
                .put("/api/v1/party-invites/anytoken0000001/participants/me/realtime-profile") {
                    header("Authorization", "Bearer not-a-jwt")
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(body)
                }.andExpect {
                    status { isUnauthorized() }
                    jsonPath("$.error.code") { value("AUTH_INVALID_TOKEN") }
                }
        }
```

- [ ] **Step 17: 테스트 실행 확인**

```bash
./gradlew test --tests "com.team2.server.party.controller.ParticipantRealtimeProfileControllerTest"
```

Expected: 20 tests passed.

`User` 생성자 시그니처가 실제 코드와 다르면 컴파일 에러가 난다. `src/main/kotlin/com/team2/server/user/entity/User.kt`와 다른 테스트 파일의 `saveUser` 호출부를 참조해 시그니처를 맞춘다.

- [ ] **Step 18: 커밋**

```bash
git add src/test/kotlin/com/team2/server/party/controller/ParticipantRealtimeProfileControllerTest.kt
git commit -m "test: 실시간 프로필 Controller 통합 테스트 추가"
```

---

## Task 10: 전체 빌드/테스트 + ArchUnit 검증

**Files:**

- 없음(검증만)

- [ ] **Step 1: 전체 단위 빌드**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. 모든 테스트 통과.

테스트 실패 시:

- ArchUnit `LayerDependencyTest`, `PackageStructureTest`, `ForbiddenCallRuleTest`, `AnnotationRuleTest`, `CrossFeatureRuleTest` 모두 통과해야 한다.
- ArchUnit 위반이 나면 다음을 점검한다:
  - Controller에서 Repository 직접 import 없음
  - UseCase에서 다른 feature의 service 호출 없음
  - Service에 `@Transactional` 없음
  - UseCase에 `@Transactional` 있음
  - Service public 메서드 5개 이내, 의존성 4개 이내
  - UseCase 1 public 메서드(`invoke`), 의존성 5개 이내, 60줄 이내
- 위반된 클래스를 spec과 이 plan 기준으로 수정한다.

- [ ] **Step 2: 컨테이너 누수 확인**

```bash
docker ps -a --filter "label=org.testcontainers"
```

Expected: 0개.

(만약 Testcontainers를 사용하는 테스트가 있다면 H2-only 설정으로 단순 통과되거나, `TestcontainersConfiguration`이 `@Import`된 테스트에서만 컨테이너가 뜬다. 이번 추가 테스트는 기존 `@SpringBootTest` 패턴을 그대로 따르므로 별도 컨테이너 설정 부담 없음.)

- [ ] **Step 3: Swagger 확인용 로컬 부팅(선택)**

빠른 수동 검증 원하면:

```bash
./gradlew bootRun
```

브라우저: `http://localhost:8080/swagger-ui/index.html` → "Party Invite" 태그에서 새 GET/PUT 항목 표시.

종료: Ctrl+C.

이 단계는 옵션이고 필수 아님. CI가 빌드/테스트 통과만 본다.

- [ ] **Step 4: 최종 확인 커밋 (필요한 경우)**

이 단계에서 새 파일이 없으면 커밋 없이 종료.

`git status`에 변경된 파일이 없는지 확인:

```bash
git status
```

Expected: nothing to commit, working tree clean.

---

## Self-Review

(이 절은 plan 작성자 self-review 결과를 기록. 실행자는 무시해도 된다.)

**1. Spec coverage:**

| Spec 섹션 | 구현 Task |
|---|---|
| 1. 결정 요약 | Task 5-8 전반에 반영 |
| 2. 사용자 흐름 | Task 5-6 UseCase가 흐름을 구현 |
| 3-1. 닉네임 정책 | Task 2 (Request DTO `@Size(max=10)`, `@NotBlank`), Task 6 (`trim()` + blank 가드) |
| 3-2. 캐릭터 정책 | Task 2 (Request `@NotNull` Long?), Task 6 (`CHARACTER_NOT_FOUND` 분기) |
| 4-1. GET API | Task 5 (UseCase), Task 7-8 (Controller), Task 9 Step 4-9 (테스트) |
| 4-2. PUT API | Task 6 (UseCase), Task 7-8 (Controller), Task 9 Step 10-17 (테스트) |
| 5. 엔티티 변경점 (없음) | 명시적으로 변경 없음 — 모든 Task가 기존 엔티티 그대로 사용 |
| 6. 코드 구조 | Task 1-9 파일 추가 |
| 7. ErrorCode | Task 1 |
| 8. SecurityConfig | 추가 변경 없음 — `anyRequest().authenticated()` 기본 정책에 포함 |
| 9. Swagger | Task 7 |
| 10. 테스트 계획 | Task 3-4 (Service 단위), Task 9 (Controller 통합) — 전부 커버 |
| 11. 후속(범위 외) | plan 범위 외 |

**2. Placeholder scan:** 모든 step에 실제 코드/명령 포함. "TBD" 등 없음.

**3. Type consistency:**

- `RealtimeParticipantProfileService.upsert(participant, nickname, character, isHostNicknameLocked)` 시그니처가 Task 3 테스트와 Task 4 구현, Task 6 UseCase 호출에서 일관됨.
- `ParticipantRealtimeProfileResult` 필드명이 Result/Response 양쪽에서 일관됨.
- `Character` 엔티티 생성자(`name = ...`) 형태가 테스트/실제 코드와 일치.

이상 없음.

---

## 실행 핸드오프 안내

Plan 저장 위치: `docs/superpowers/plans/2026-05-14-party-participant-info.md`

실행은 두 가지 방식 중 선택:

1. **Subagent-Driven (권장)** — task별로 새 subagent 한 개씩 dispatch, task 간 리뷰
2. **Inline Execution** — 같은 세션에서 batch 실행, checkpoint마다 리뷰

이번 흐름에서는 plan 작성 직후 `team-flow` 스킬로 이슈/브랜치를 만들고 본 구현은 별도 세션/실행 도구에서 진행한다(사용자 지시).
