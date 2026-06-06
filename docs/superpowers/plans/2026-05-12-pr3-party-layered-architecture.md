# PR 3 — party 4-레이어 마이그레이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `com.team2.server.party` 패키지를 `api / application{usecase,service,dto} / domain / infrastructure` 4-레이어로 재배치하고, Controller → UseCase 직결 + `@Transactional` 위치를 UseCase 로 이전한다. chat PR 7 의 cross-feature 정렬을 위한 party Service 표면도 사전 보강한다. ArchUnit 룰은 PR 8 에서 일괄 활성하므로 본 PR 에서는 활성하지 않는다 (구조 정렬만).

**Architecture:**
- party 의 7개 entity + 5개 repository + 3개 service + 4개 usecase + 10개 controller/dto 파일을 4-레이어 위치로 `git mv` (history 보존).
- 신규 UseCase 3종(`CreatePartyUseCase`, `DeletePartyUseCase`, `ActivateInviteLinkUseCase`) 도입 — Controller 가 Service 를 직접 호출하지 않도록 한다.
- `*Request`/`*Response` 는 `api/dto`, `*Command`/`*Result` 는 `application/dto`. Controller 가 매핑.
- chat PR 7 의 cross-feature 정렬에 필요한 Service 메서드(`PartyService.findActiveRealtimeParty`, `ParticipantService.findOrCreate`, `PartyInviteService.resolveValidInvite`)를 사전 도입.

**Tech Stack:** Kotlin 1.9, Spring Boot 3, JPA/Hibernate, JUnit 5, Mockito-kotlin, ArchUnit.

**Scope Out:**
- chat / rollingpaper 자체의 4-레이어 정렬 — 각각 PR 7 / PR 6.
- ArchUnit 룰 활성화 — PR 8.
- cross-feature 직접 의존(chat/rollingpaper → party.entity/repository) 제거 — PR 6/PR 7. 본 PR 은 새 패키지로 import 만 갱신.
- `chat.repository.ChatMessageRepository`, `rollingpaper.repository.RollingPaperRepository` 자체 위치 — 각자 PR.

**완료 후 상태:**
- `src/main/kotlin/com/team2/server/party/{api,application/{usecase,service,dto},domain/entity,infrastructure/{persistence,}}` 디렉터리만 존재
- 기존 `party/{controller,dto,entity,repository,service,usecase}` 디렉터리 0개
- `./gradlew build` GREEN
- 기존 모든 통합/단위 테스트 통과 (테스트 개수 회귀 없음)

---

## 0. 파일 구조 (목표 상태)

```
src/main/kotlin/com/team2/server/party/
├── api/
│   ├── CharacterApi.kt
│   ├── CharacterController.kt
│   ├── MePartyApi.kt
│   ├── MePartyController.kt
│   ├── PartyApi.kt
│   ├── PartyController.kt
│   ├── PartyInviteApi.kt
│   ├── PartyInviteController.kt
│   ├── PartyInviteLookupApi.kt
│   ├── PartyInviteLookupController.kt
│   └── dto/
│       ├── ActivateInviteLinkResponse.kt
│       ├── CharacterResponse.kt
│       ├── CreatePaperOnlyPartyRequest.kt
│       ├── CreatePartyResponse.kt
│       ├── CreateRealtimePartyRequest.kt
│       ├── PartyInviteLookupResponse.kt              (RealtimeSchedule 포함)
│       ├── PartyInviteParticipationResponse.kt
│       └── UpcomingPartyResponse.kt                  (UpcomingRealtimeScheduleResponse 포함)
├── application/
│   ├── dto/
│   │   ├── CharacterResult.kt                        (이미 존재)
│   │   ├── CreatePaperOnlyPartyCommand.kt           (신규)
│   │   ├── CreateRealtimePartyCommand.kt            (신규)
│   │   ├── PartyInviteLookupResult.kt               (신규, RealtimeScheduleResult 포함)
│   │   └── UpcomingPartyResult.kt                   (신규, UpcomingRealtimeScheduleResult 포함)
│   ├── service/
│   │   ├── ParticipantService.kt
│   │   ├── PartyInviteService.kt
│   │   └── PartyService.kt
│   └── usecase/
│       ├── ActivateInviteLinkUseCase.kt             (신규)
│       ├── CreatePartyUseCase.kt                    (신규)
│       ├── DeletePartyUseCase.kt                    (신규)
│       ├── GetCharactersUseCase.kt
│       ├── GetUpcomingPartiesUseCase.kt
│       ├── JoinPartyInviteUseCase.kt
│       ├── LookupPartyInviteUseCase.kt
├── domain/entity/
│   ├── Character.kt
│   ├── PaperOnlyParty.kt
│   ├── Participant.kt
│   ├── Party.kt                                      (PartyOption, PartyPurpose enum 포함)
│   ├── PartyInvite.kt
│   ├── RealtimeParticipantProfile.kt
│   └── RealtimeParty.kt                              (RealtimePartyStatus enum 포함)
└── infrastructure/
    ├── CharacterImageResolver.kt                     (CharacterImageUrlResolver → 리네임)
    └── persistence/
        ├── CharacterRepository.kt
        ├── ParticipantRepository.kt
        ├── PartyInviteRepository.kt
        ├── PartyRepository.kt
        └── RealtimeParticipantProfileRepository.kt
```

테스트 패키지도 동일 구조로 동기화:

```
src/test/kotlin/com/team2/server/party/
├── api/                                              ← controller/ 에서 이동
├── application/
│   ├── service/                                      ← service/ 에서 이동
│   └── usecase/                                      (신규 UseCase 테스트 추가)
├── domain/entity/                                    ← entity/ 에서 이동
└── infrastructure/persistence/                       ← repository/ 에서 이동
```

---

## Task 0: Baseline Build 확인

작업 시작 전 GREEN 상태인지 확인. 이후 각 phase 의 RED→GREEN 추적이 의미 있도록.

- [ ] **Step 1: 현재 브랜치 확인 (develop 베이스에서 작업)**

```bash
git status --short
git branch --show-current
git log -1 --oneline
```

Expected:
- Working tree clean (또는 알려진 untracked 파일만)
- Branch: `refactor/pr3-party-layered` 등 PR 3 전용 브랜치 (없으면 생성: `git checkout -b refactor/pr3-party-layered`)

- [ ] **Step 2: Baseline build**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. 모든 테스트 통과.

만약 FAIL 이면 PR 3 작업 전에 main 트리 상태부터 점검. 마이그레이션 시작 금지.

---

## Task 1: Domain Layer — entity 7종 이동

`party/entity/*` → `party/domain/entity/*`. enum 들도 같은 파일에 따라간다.

**Files:**
- Move: 7개
  - `src/main/kotlin/com/team2/server/party/entity/Character.kt` → `party/domain/entity/Character.kt`
  - `party/entity/PaperOnlyParty.kt` → `party/domain/entity/PaperOnlyParty.kt`
  - `party/entity/Participant.kt` → `party/domain/entity/Participant.kt`
  - `party/entity/Party.kt` → `party/domain/entity/Party.kt`
  - `party/entity/PartyInvite.kt` → `party/domain/entity/PartyInvite.kt`
  - `party/entity/RealtimeParticipantProfile.kt` → `party/domain/entity/RealtimeParticipantProfile.kt`
  - `party/entity/RealtimeParty.kt` → `party/domain/entity/RealtimeParty.kt`
- Move tests: 3개
  - `src/test/kotlin/com/team2/server/party/entity/PartyDomainTest.kt` → `party/domain/entity/PartyDomainTest.kt`
  - `party/entity/PartyParticipationDomainTest.kt` → `party/domain/entity/PartyParticipationDomainTest.kt`
  - `party/entity/PartyStatusTest.kt` → `party/domain/entity/PartyStatusTest.kt`

- [ ] **Step 1: 디렉터리 생성 + git mv**

```bash
mkdir -p src/main/kotlin/com/team2/server/party/domain/entity
mkdir -p src/test/kotlin/com/team2/server/party/domain/entity

git mv src/main/kotlin/com/team2/server/party/entity/Character.kt src/main/kotlin/com/team2/server/party/domain/entity/Character.kt
git mv src/main/kotlin/com/team2/server/party/entity/PaperOnlyParty.kt src/main/kotlin/com/team2/server/party/domain/entity/PaperOnlyParty.kt
git mv src/main/kotlin/com/team2/server/party/entity/Participant.kt src/main/kotlin/com/team2/server/party/domain/entity/Participant.kt
git mv src/main/kotlin/com/team2/server/party/entity/Party.kt src/main/kotlin/com/team2/server/party/domain/entity/Party.kt
git mv src/main/kotlin/com/team2/server/party/entity/PartyInvite.kt src/main/kotlin/com/team2/server/party/domain/entity/PartyInvite.kt
git mv src/main/kotlin/com/team2/server/party/entity/RealtimeParticipantProfile.kt src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParticipantProfile.kt
git mv src/main/kotlin/com/team2/server/party/entity/RealtimeParty.kt src/main/kotlin/com/team2/server/party/domain/entity/RealtimeParty.kt

git mv src/test/kotlin/com/team2/server/party/entity/PartyDomainTest.kt src/test/kotlin/com/team2/server/party/domain/entity/PartyDomainTest.kt
git mv src/test/kotlin/com/team2/server/party/entity/PartyParticipationDomainTest.kt src/test/kotlin/com/team2/server/party/domain/entity/PartyParticipationDomainTest.kt
git mv src/test/kotlin/com/team2/server/party/entity/PartyStatusTest.kt src/test/kotlin/com/team2/server/party/domain/entity/PartyStatusTest.kt
```

- [ ] **Step 2: 옮긴 7개 main 파일의 첫 줄 `package` 갱신**

각 파일의 첫 줄 `package com.team2.server.party.entity` → `package com.team2.server.party.domain.entity` 로 변경. Edit 도구 사용.

- [ ] **Step 3: 옮긴 3개 test 파일의 첫 줄 `package` 갱신**

각 파일의 `package com.team2.server.party.entity` → `package com.team2.server.party.domain.entity`.

- [ ] **Step 4: 내부 import 일괄 갱신**

party 외부에서 `com.team2.server.party.entity.*` 를 참조하는 모든 곳의 import 를 `com.team2.server.party.domain.entity.*` 로 교체.

대상 파일 (탐사 결과 기준):
- `party/controller/*.kt` (10개) — 컨트롤러가 entity import 한 경우
- `party/dto/*.kt` (10개) — DTO 가 entity import 한 경우
- `party/repository/*.kt` (5개)
- `party/service/*.kt` (3개)
- `party/usecase/*.kt` (4개)
- `chat/entity/ChatMessage.kt`
- `chat/usecase/EnterAndSubscribeChatUseCase.kt`
- `chat/usecase/EnterRealtimePartyUseCase.kt`
- `chat/usecase/SendChatMessageUseCase.kt`
- `rollingpaper/dto/RollingPaperListResponse.kt`
- `rollingpaper/entity/RollingPaper.kt`
- `rollingpaper/repository/RollingPaperRepository.kt`
- `rollingpaper/usecase/CreateRollingPaperUseCase.kt`
- `rollingpaper/usecase/GetRollingPaperDetailUseCase.kt`
- `rollingpaper/usecase/GetRollingPaperListUseCase.kt`

테스트 파일도 같이 (탐사 결과):
- `chat/controller/ChatControllerTest.kt`
- `chat/usecase/EnterAndSubscribeChatUseCaseTest.kt`
- `chat/usecase/EnterRealtimePartyUseCaseTest.kt`
- `chat/usecase/SendChatMessageUseCaseTest.kt`
- `rollingpaper/controller/RollingPaperControllerTest.kt`
- `rollingpaper/controller/RollingPaperListControllerTest.kt`
- 그 외 — Step 5 에서 grep 으로 모두 찾는다.

대량 치환 권장 명령 (확인 후 사용):

```bash
grep -rln "com.team2.server.party.entity" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.entity|com.team2.server.party.domain.entity|g'
```

macOS BSD sed 기준. 다른 환경이면 `sed -i` 로 변경.

- [ ] **Step 5: 남은 참조 점검 (확인)**

```bash
grep -rn "com\.team2\.server\.party\.entity" src/main/kotlin src/test/kotlin
```

Expected: 매치 0건. (있으면 Step 4 sed 누락이거나, 동적 String 참조 — 수동 수정)

- [ ] **Step 6: Compile + test**

```bash
./gradlew compileKotlin compileTestKotlin
```

Expected: BUILD SUCCESSFUL.

```bash
./gradlew test
```

Expected: 모든 테스트 통과. 테스트 개수가 Task 0 기준과 동일한지 출력으로 확인.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/team2/server/party/domain \
        src/test/kotlin/com/team2/server/party/domain \
        $(grep -rln "party\.domain\.entity" src/main/kotlin src/test/kotlin)
git commit -m "refactor: party entity 를 domain/entity 로 이동"
```

(별도로 `git status` 로 남은 stage 항목이 없는지 확인)

---

## Task 2: Infrastructure Persistence — repository 5종 이동 + repository @Transactional 정리

`party/repository/*` → `party/infrastructure/persistence/*`. 추가로 repository 메서드의 `@Transactional` 을 제거(C2-method 룰 사전 준비).

**Files:**
- Move: 5개
  - `party/repository/CharacterRepository.kt` → `party/infrastructure/persistence/CharacterRepository.kt`
  - `party/repository/ParticipantRepository.kt` → `party/infrastructure/persistence/ParticipantRepository.kt`
  - `party/repository/PartyInviteRepository.kt` → `party/infrastructure/persistence/PartyInviteRepository.kt`
  - `party/repository/PartyRepository.kt` → `party/infrastructure/persistence/PartyRepository.kt`
  - `party/repository/RealtimeParticipantProfileRepository.kt` → `party/infrastructure/persistence/RealtimeParticipantProfileRepository.kt`
- Move test: 1개
  - `src/test/kotlin/com/team2/server/party/repository/ParticipantRepositoryTest.kt` → `party/infrastructure/persistence/ParticipantRepositoryTest.kt`
- Modify: `PartyInviteRepository.kt`, `RealtimeParticipantProfileRepository.kt` (`@Transactional` 제거)

- [ ] **Step 1: 디렉터리 생성 + git mv**

```bash
mkdir -p src/main/kotlin/com/team2/server/party/infrastructure/persistence
mkdir -p src/test/kotlin/com/team2/server/party/infrastructure/persistence

git mv src/main/kotlin/com/team2/server/party/repository/CharacterRepository.kt src/main/kotlin/com/team2/server/party/infrastructure/persistence/CharacterRepository.kt
git mv src/main/kotlin/com/team2/server/party/repository/ParticipantRepository.kt src/main/kotlin/com/team2/server/party/infrastructure/persistence/ParticipantRepository.kt
git mv src/main/kotlin/com/team2/server/party/repository/PartyInviteRepository.kt src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyInviteRepository.kt
git mv src/main/kotlin/com/team2/server/party/repository/PartyRepository.kt src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyRepository.kt
git mv src/main/kotlin/com/team2/server/party/repository/RealtimeParticipantProfileRepository.kt src/main/kotlin/com/team2/server/party/infrastructure/persistence/RealtimeParticipantProfileRepository.kt

git mv src/test/kotlin/com/team2/server/party/repository/ParticipantRepositoryTest.kt src/test/kotlin/com/team2/server/party/infrastructure/persistence/ParticipantRepositoryTest.kt
```

- [ ] **Step 2: 옮긴 6개 파일의 `package` 라인 갱신**

`package com.team2.server.party.repository` → `package com.team2.server.party.infrastructure.persistence`.

- [ ] **Step 3: 외부 import 일괄 갱신**

```bash
grep -rln "com.team2.server.party.repository" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.repository|com.team2.server.party.infrastructure.persistence|g'
```

확인:

```bash
grep -rn "com\.team2\.server\.party\.repository" src/main/kotlin src/test/kotlin
```

Expected: 0건.

- [ ] **Step 4: `PartyInviteRepository.deleteAllByPartyId` 의 `@Transactional` 제거**

`src/main/kotlin/com/team2/server/party/infrastructure/persistence/PartyInviteRepository.kt`:

기존:
```kotlin
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
...
    @Modifying
    @Transactional
    fun deleteAllByPartyId(partyId: Long)
```

변경 후:
```kotlin
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
...
    @Modifying
    fun deleteAllByPartyId(partyId: Long)
```

(`import org.springframework.transaction.annotation.Transactional` 줄 삭제 + `@Transactional` 어노테이션 삭제)

이 메서드의 유일한 호출 지점은 `PartyService.deleteParty` (Task 6 에서 UseCase 로 이전) — 호출자 트랜잭션 안에서 동작하므로 안전.

- [ ] **Step 5: `RealtimeParticipantProfileRepository.deleteAllByParticipantIdIn` 의 `@Transactional` 제거**

`src/main/kotlin/com/team2/server/party/infrastructure/persistence/RealtimeParticipantProfileRepository.kt`:

기존:
```kotlin
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional
...
    @Modifying
    @Transactional
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
```

변경 후:
```kotlin
import org.springframework.data.jpa.repository.Modifying
...
    @Modifying
    fun deleteAllByParticipantIdIn(participantIds: List<Long>)
```

(`Transactional` import + 어노테이션 삭제)

- [ ] **Step 6: Compile + test**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. 모든 테스트 통과 (특히 `PartyServiceTest.deleteParty_*` 테스트가 deleteAllByPartyId 의 호출 자체는 그대로라 동일).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/team2/server/party/infrastructure \
        src/test/kotlin/com/team2/server/party/infrastructure \
        $(grep -rln "party\.infrastructure\.persistence" src/main/kotlin src/test/kotlin)
git commit -m "refactor: party repository 를 infrastructure/persistence 로 이동 + repository @Transactional 제거"
```

---

## Task 3: Infrastructure — CharacterImageResolver 이동 + 리네임

`party/dto/CharacterImageUrlResolver.kt` (`@Component`, DTO 가 아님) → `party/infrastructure/CharacterImageResolver.kt` 로 이동 + 클래스명 정렬.

**Files:**
- Move + rename: `party/dto/CharacterImageUrlResolver.kt` → `party/infrastructure/CharacterImageResolver.kt`

> 탐사 결과: 현재 caller 가 없는 dead code 상태. 단 entity 사용 예약 슬롯이므로 제거하지 않고 위치만 정렬.

- [ ] **Step 1: git mv + 파일 내부 갱신**

```bash
git mv src/main/kotlin/com/team2/server/party/dto/CharacterImageUrlResolver.kt src/main/kotlin/com/team2/server/party/infrastructure/CharacterImageResolver.kt
```

파일 내용 수정:
- `package com.team2.server.party.dto` → `package com.team2.server.party.infrastructure`
- `class CharacterImageUrlResolver(` → `class CharacterImageResolver(`
- entity import: `com.team2.server.party.entity.Character` → `com.team2.server.party.domain.entity.Character` (Task 1 이후라면 이미 sed 처리되어 있을 것 — 재확인)

- [ ] **Step 2: 호출자 확인**

```bash
grep -rn "CharacterImageUrlResolver\|CharacterImageResolver" src/main/kotlin src/test/kotlin
```

Expected: 새 파일 내 1건만. (다른 caller 가 발견되면 클래스명 갱신 — 사전 탐사에는 없음.)

- [ ] **Step 3: Compile**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/team2/server/party/infrastructure/CharacterImageResolver.kt
git commit -m "refactor: CharacterImageUrlResolver 를 infrastructure 로 이동하고 CharacterImageResolver 로 리네임"
```

---

## Task 4: Application Service — 기존 3개 Service 이동

`party/service/*` → `party/application/service/*`.

**Files:**
- Move: 3개
  - `party/service/ParticipantService.kt` → `party/application/service/ParticipantService.kt`
  - `party/service/PartyInviteService.kt` → `party/application/service/PartyInviteService.kt`
  - `party/service/PartyService.kt` → `party/application/service/PartyService.kt`
- Move tests: 2개
  - `src/test/kotlin/com/team2/server/party/service/PartyInviteServiceTest.kt` → `party/application/service/PartyInviteServiceTest.kt`
  - `party/service/PartyServiceTest.kt` → `party/application/service/PartyServiceTest.kt`

> 본 Task 는 위치 이동만 수행한다. `@Transactional` 제거 / orchestration 분리 / UseCase 도입은 Task 6/Task 7 에서.

- [ ] **Step 1: 디렉터리 생성 + git mv**

```bash
mkdir -p src/main/kotlin/com/team2/server/party/application/service
mkdir -p src/test/kotlin/com/team2/server/party/application/service

git mv src/main/kotlin/com/team2/server/party/service/ParticipantService.kt src/main/kotlin/com/team2/server/party/application/service/ParticipantService.kt
git mv src/main/kotlin/com/team2/server/party/service/PartyInviteService.kt src/main/kotlin/com/team2/server/party/application/service/PartyInviteService.kt
git mv src/main/kotlin/com/team2/server/party/service/PartyService.kt src/main/kotlin/com/team2/server/party/application/service/PartyService.kt

git mv src/test/kotlin/com/team2/server/party/service/PartyInviteServiceTest.kt src/test/kotlin/com/team2/server/party/application/service/PartyInviteServiceTest.kt
git mv src/test/kotlin/com/team2/server/party/service/PartyServiceTest.kt src/test/kotlin/com/team2/server/party/application/service/PartyServiceTest.kt
```

- [ ] **Step 2: 이동된 5개 파일 `package` 갱신**

`package com.team2.server.party.service` → `package com.team2.server.party.application.service`.

- [ ] **Step 3: 외부 import 갱신**

```bash
grep -rln "com.team2.server.party.service" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.service|com.team2.server.party.application.service|g'

grep -rn "com\.team2\.server\.party\.service" src/main/kotlin src/test/kotlin
```

두 번째 명령 expected: 0건.

> 갱신 대상에 포함되는 외부: `rollingpaper/usecase/CreateRollingPaperUseCase.kt` (ParticipantService, PartyInviteService).

- [ ] **Step 4: Compile + test**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL. 기존 모든 테스트 통과.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/team2/server/party/application \
        src/test/kotlin/com/team2/server/party/application \
        $(grep -rln "party\.application\.service" src/main/kotlin src/test/kotlin)
git commit -m "refactor: party service 를 application/service 로 이동"
```

---

## Task 5: Application DTO — Command / Result 신규 도입

UseCase 가 api/dto 에 의존하지 않도록, `application/dto/` 에 Command/Result 타입을 신규 생성. 기존 `CharacterResult` 는 별도 이동(Step 6).

**Files:**
- Create:
  - `src/main/kotlin/com/team2/server/party/application/dto/CreatePaperOnlyPartyCommand.kt`
  - `src/main/kotlin/com/team2/server/party/application/dto/CreateRealtimePartyCommand.kt`
  - `src/main/kotlin/com/team2/server/party/application/dto/PartyInviteLookupResult.kt` (RealtimeScheduleResult 포함)
  - `src/main/kotlin/com/team2/server/party/application/dto/UpcomingPartyResult.kt` (UpcomingRealtimeScheduleResult 포함)
- Move: `src/main/kotlin/com/team2/server/party/dto/CharacterResult.kt` → `party/application/dto/CharacterResult.kt`

- [ ] **Step 1: 디렉터리 + CharacterResult 이동**

```bash
mkdir -p src/main/kotlin/com/team2/server/party/application/dto
git mv src/main/kotlin/com/team2/server/party/dto/CharacterResult.kt src/main/kotlin/com/team2/server/party/application/dto/CharacterResult.kt
```

`CharacterResult.kt` 의 package 갱신: `package com.team2.server.party.dto` → `package com.team2.server.party.application.dto`.

- [ ] **Step 2: 외부 import 갱신 (`CharacterResult`)**

```bash
grep -rln "com.team2.server.party.dto.CharacterResult" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.dto\.CharacterResult|com.team2.server.party.application.dto.CharacterResult|g'
```

대상: `party/usecase/GetCharactersUseCase.kt`, `party/dto/CharacterResponse.kt` (구 위치 — 현재).

- [ ] **Step 3: `CreatePaperOnlyPartyCommand.kt` 생성**

```kotlin
package com.team2.server.party.application.dto

import java.time.LocalDate

data class CreatePaperOnlyPartyCommand(
    val celebrantNickname: String,
    val startedDate: LocalDate,
)
```

- [ ] **Step 4: `CreateRealtimePartyCommand.kt` 생성**

```kotlin
package com.team2.server.party.application.dto

import java.time.LocalDate
import java.time.LocalTime

data class CreateRealtimePartyCommand(
    val celebrantNickname: String,
    val startedDate: LocalDate,
    val startTime: LocalTime,
    val characterId: Long,
)
```

- [ ] **Step 5: `PartyInviteLookupResult.kt` 생성**

기존 `PartyInviteLookupResponse` 와 `RealtimeSchedule` 의 필드를 그대로 옮기되 Swagger/Jackson 어노테이션 제거.

```kotlin
package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.PartyOption
import java.time.LocalDate
import java.time.LocalDateTime

data class PartyInviteLookupResult(
    val partyId: Long,
    val celebrantNickname: String?,
    val isHost: Boolean,
    val partyOption: PartyOption,
    val partyEnded: Boolean,
    val rollingPaperWritten: Boolean,
    val partyStartDate: LocalDate,
    val partyEndDate: LocalDate,
    val realtimeSchedule: RealtimeScheduleResult?,
)

data class RealtimeScheduleResult(
    val liveStartAt: LocalDateTime,
    val enterableFrom: LocalDateTime,
    val liveEndAt: LocalDateTime,
    val liveDurationMinutes: Long,
)
```

- [ ] **Step 6: `UpcomingPartyResult.kt` 생성**

```kotlin
package com.team2.server.party.application.dto

import com.team2.server.party.domain.entity.PartyOption
import java.time.LocalDateTime

data class UpcomingPartyResult(
    val partyId: Long,
    val inviteToken: String?,
    val partyOption: PartyOption,
    val celebrantNickname: String?,
    val partyStartedAt: LocalDateTime,
    val partyEndedAt: LocalDateTime,
    val isHost: Boolean,
    val rollingPaperWritten: Boolean,
    val hostRollingPaperOpenAt: LocalDateTime?,
    val realtimeSchedule: UpcomingRealtimeScheduleResult?,
)

data class UpcomingRealtimeScheduleResult(
    val enterableFrom: LocalDateTime,
    val liveStartAt: LocalDateTime,
    val liveEndAt: LocalDateTime,
)
```

- [ ] **Step 7: Compile (UseCase 가 아직 새 타입을 사용 안 하므로 그대로 통과해야 함)**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL. 새 Command/Result 는 아직 미사용 — 다음 Task 에서 사용.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/team2/server/party/application/dto \
        $(grep -rln "party\.application\.dto" src/main/kotlin src/test/kotlin)
git commit -m "feat: party application/dto 에 Command/Result 타입 도입"
```

---

## Task 6: Application UseCase — 기존 4개 이동 + 신규 3개 도입

기존 4개 UseCase 를 `application/usecase/` 로 이동하고, Controller 가 Service 직접 호출하던 자리를 메우는 3개 신규 UseCase(`CreatePartyUseCase`, `DeletePartyUseCase`, `ActivateInviteLinkUseCase`) 를 도입한다. Service 의 `@Transactional` 은 UseCase 로 이전한다.

### Task 6-a: 기존 UseCase 이동

**Files:**
- Move: 4개
  - `party/usecase/GetCharactersUseCase.kt` → `party/application/usecase/GetCharactersUseCase.kt`
  - `party/usecase/GetUpcomingPartiesUseCase.kt` → `party/application/usecase/GetUpcomingPartiesUseCase.kt`
  - `party/usecase/JoinPartyInviteUseCase.kt` → `party/application/usecase/JoinPartyInviteUseCase.kt`
  - `party/usecase/LookupPartyInviteUseCase.kt` → `party/application/usecase/LookupPartyInviteUseCase.kt`

- [ ] **Step 1: git mv**

```bash
mkdir -p src/main/kotlin/com/team2/server/party/application/usecase

git mv src/main/kotlin/com/team2/server/party/usecase/GetCharactersUseCase.kt src/main/kotlin/com/team2/server/party/application/usecase/GetCharactersUseCase.kt
git mv src/main/kotlin/com/team2/server/party/usecase/GetUpcomingPartiesUseCase.kt src/main/kotlin/com/team2/server/party/application/usecase/GetUpcomingPartiesUseCase.kt
git mv src/main/kotlin/com/team2/server/party/usecase/JoinPartyInviteUseCase.kt src/main/kotlin/com/team2/server/party/application/usecase/JoinPartyInviteUseCase.kt
git mv src/main/kotlin/com/team2/server/party/usecase/LookupPartyInviteUseCase.kt src/main/kotlin/com/team2/server/party/application/usecase/LookupPartyInviteUseCase.kt
```

- [ ] **Step 2: package 갱신**

4개 파일의 `package com.team2.server.party.usecase` → `package com.team2.server.party.application.usecase`.

- [ ] **Step 3: 외부 import 갱신**

```bash
grep -rln "com.team2.server.party.usecase" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.usecase|com.team2.server.party.application.usecase|g'

grep -rn "com\.team2\.server\.party\.usecase" src/main/kotlin src/test/kotlin
```

두 번째 expected: 0건.

- [ ] **Step 4: Compile + test**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

### Task 6-b: `LookupPartyInviteUseCase` Result 출력 전환

기존: `PartyInviteLookupResponse` 반환 (api 타입). 변경: `PartyInviteLookupResult` 반환 (application 타입).

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/usecase/LookupPartyInviteUseCase.kt`

- [ ] **Step 1: import 교체**

```kotlin
// 삭제
import com.team2.server.party.dto.PartyInviteLookupResponse
import com.team2.server.party.dto.RealtimeSchedule

// 추가
import com.team2.server.party.application.dto.PartyInviteLookupResult
import com.team2.server.party.application.dto.RealtimeScheduleResult
```

- [ ] **Step 2: 메서드 반환 타입 + 본문 교체**

`fun lookup(...): PartyInviteLookupResponse` → `fun lookup(...): PartyInviteLookupResult`.

`return PartyInviteLookupResponse(...)` → `return PartyInviteLookupResult(...)` (필드 동일).

`createRealtimeSchedule` 의 `RealtimeSchedule(...)` → `RealtimeScheduleResult(...)`. 반환 타입도 `RealtimeScheduleResult`.

- [ ] **Step 3: 호출자(`PartyInviteLookupController`) 는 아직 미수정 (Task 7 에서 매핑 처리). 일시적으로 Controller compile FAIL 이 정상.**

> 이 단계는 의도적으로 RED 상태로 두고 다음 단계에서 GREEN 으로 복귀하도록 묶는다. 또는 Step 3 까지 한 번에 처리하고 Task 7 Step 1-2 와 묶어 단일 commit 으로 처리해도 무방.

권장: **Task 6-b 와 Task 7 의 PartyInviteLookupController 부분을 합쳐 한 번에 진행**. 이 plan 에서는 진도 관리를 위해 분리.

### Task 6-c: `GetUpcomingPartiesUseCase` Result 출력 전환

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/usecase/GetUpcomingPartiesUseCase.kt`

- [ ] **Step 1: import 교체**

```kotlin
// 삭제
import com.team2.server.party.dto.UpcomingPartyResponse
import com.team2.server.party.dto.UpcomingRealtimeScheduleResponse

// 추가
import com.team2.server.party.application.dto.UpcomingPartyResult
import com.team2.server.party.application.dto.UpcomingRealtimeScheduleResult
```

- [ ] **Step 2: 메서드 반환 타입 + 본문 교체**

`fun getUpcomingParties(...): List<UpcomingPartyResponse>` → `fun getUpcomingParties(...): List<UpcomingPartyResult>`.

본문에서 `UpcomingPartyResponse(...)` → `UpcomingPartyResult(...)` (필드 동일).

`RealtimeParty.toRealtimeSchedule()` 내부 `UpcomingRealtimeScheduleResponse(...)` → `UpcomingRealtimeScheduleResult(...)`. 반환 타입도 변경.

> Step 3 caller(`MePartyController`) compile 도 일시 RED — Task 7 에서 복구.

### Task 6-d: `JoinPartyInviteUseCase` Result 출력 전환 (primitive 단일값)

스펙 §3-3 의 예외: 단일 primitive 반환은 래핑 안 함. `participantId: Long` 만 반환.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/usecase/JoinPartyInviteUseCase.kt`

- [ ] **Step 1: 반환 타입 변경**

```kotlin
// 삭제
import com.team2.server.party.dto.PartyInviteParticipationResponse
...
    fun join(inviteToken: String, userId: Long): PartyInviteParticipationResponse {
        ...
        return PartyInviteParticipationResponse(participantId = participant.id)
    }
```

```kotlin
// 변경 후 (import 1줄 삭제)
    fun join(inviteToken: String, userId: Long): Long {
        ...
        return participant.id
    }
```

> caller(`PartyInviteLookupController`) 일시 RED — Task 7 에서 매핑.

### Task 6-e: 신규 `CreatePartyUseCase` 도입

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/CreatePartyUseCase.kt`
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/CreatePartyUseCaseTest.kt`

- [ ] **Step 1: 테스트 먼저 작성 (TDD RED)**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.service.PartyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CreatePartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @InjectMocks
    lateinit var useCase: CreatePartyUseCase

    @Test
    fun `createRealtime delegates to partyService and returns partyId`() {
        val command = CreateRealtimePartyCommand(
            celebrantNickname = "홍길동",
            startedDate = LocalDate.of(2026, 6, 1),
            startTime = LocalTime.of(20, 0),
            characterId = 1L,
        )
        whenever(partyService.createRealtimeParty(userId = 42L, command = command)).thenReturn(100L)

        val partyId = useCase.createRealtime(userId = 42L, command = command)

        assertEquals(100L, partyId)
        verify(partyService).createRealtimeParty(userId = 42L, command = command)
    }

    @Test
    fun `createPaperOnly delegates to partyService and returns partyId`() {
        val command = CreatePaperOnlyPartyCommand(
            celebrantNickname = "홍길동",
            startedDate = LocalDate.of(2026, 6, 1),
        )
        whenever(partyService.createPaperOnlyParty(userId = 42L, command = command)).thenReturn(101L)

        val partyId = useCase.createPaperOnly(userId = 42L, command = command)

        assertEquals(101L, partyId)
        verify(partyService).createPaperOnlyParty(userId = 42L, command = command)
    }
}
```

> 이 테스트는 `PartyService.createRealtimeParty(userId, command)` / `createPaperOnlyParty(userId, command)` 시그니처를 가정. Step 2 에서 Service 시그니처도 같이 정합화.

- [ ] **Step 2: `PartyService` 시그니처 정합화 (orchestration 메서드 분리)**

`src/main/kotlin/com/team2/server/party/application/service/PartyService.kt` 의 `createRealtimeParty` / `createPaperOnlyParty` / `deleteParty` 의 **`@Transactional` 어노테이션 제거** + 시그니처/입력을 Command 기반으로 변경.

```kotlin
package com.team2.server.party.application.service

import com.team2.server.chat.repository.ChatMessageRepository  // 임시 — chat PR 7 에서 정렬
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.RealtimeParticipantProfile
import com.team2.server.party.domain.entity.RealtimeParty
import com.team2.server.party.infrastructure.persistence.CharacterRepository
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.party.infrastructure.persistence.PartyInviteRepository
import com.team2.server.party.infrastructure.persistence.PartyRepository
import com.team2.server.party.infrastructure.persistence.RealtimeParticipantProfileRepository
import com.team2.server.rollingpaper.repository.RollingPaperRepository  // 임시 — rollingpaper PR 6 에서 정렬
import com.team2.server.user.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PartyService(
    private val partyRepository: PartyRepository,
    private val participantRepository: ParticipantRepository,
    private val realtimeParticipantProfileRepository: RealtimeParticipantProfileRepository,
    private val characterRepository: CharacterRepository,
    private val partyInviteRepository: PartyInviteRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val rollingPaperRepository: RollingPaperRepository,
    private val userRepository: UserRepository,
) {
    fun createRealtimeParty(userId: Long, command: CreateRealtimePartyCommand): Long {
        val user = findUser(userId)
        val party = RealtimeParty(
            ownerId = userId,
            celebrantNickname = command.celebrantNickname,
            startedAt = LocalDateTime.of(command.startedDate, command.startTime),
        )
        val saved = partyRepository.save(party)
        val participant = participantRepository.save(
            Participant(party = saved, user = user, isCelebrant = true),
        )
        val character = characterRepository.findById(command.characterId)
            .orElseThrow { BusinessException(ErrorCode.CHARACTER_NOT_FOUND) }
        realtimeParticipantProfileRepository.save(
            RealtimeParticipantProfile(
                participant = participant,
                nickname = command.celebrantNickname,
                character = character,
            ),
        )
        return saved.id
    }

    fun createPaperOnlyParty(userId: Long, command: CreatePaperOnlyPartyCommand): Long {
        val user = findUser(userId)
        val party = PaperOnlyParty(
            ownerId = userId,
            celebrantNickname = command.celebrantNickname,
            startedAt = command.startedDate.atStartOfDay(),
        )
        val saved = partyRepository.save(party)
        participantRepository.save(
            Participant(party = saved, user = user, isCelebrant = true),
        )
        return saved.id
    }

    fun deleteParty(partyId: Long, userId: Long) {
        val party = findParty(partyId)
        if (party.ownerId != userId) throw BusinessException(ErrorCode.PARTY_FORBIDDEN)
        if (!LocalDateTime.now().isBefore(party.startedAt)) {
            throw BusinessException(ErrorCode.PARTY_ALREADY_STARTED)
        }
        val participants = participantRepository.findAllByPartyId(partyId)
        chatMessageRepository.deleteAllByPartyId(partyId)
        rollingPaperRepository.deleteAllByPartyId(partyId)
        if (party is RealtimeParty) {
            val participantIds = participants.map { it.id }
            realtimeParticipantProfileRepository.deleteAllByParticipantIdIn(participantIds)
        }
        participantRepository.deleteAll(participants)
        partyInviteRepository.deleteAllByPartyId(partyId)
        partyRepository.delete(party)
    }

    private fun findParty(partyId: Long) =
        partyRepository.findPartyById(partyId)
            ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)

    private fun findUser(userId: Long) =
        userRepository.findById(userId)
            .orElseThrow { BusinessException(ErrorCode.AUTH_USER_NOT_FOUND) }
}
```

(주요 변경: `@Transactional` 어노테이션 3개 모두 제거, 입력 타입 `*Request` → `*Command`, 반환 `CreatePartyResponse` → `Long`)

이 시점에 `PartyServiceTest` 가 RED — Step 3 에서 같이 갱신.

- [ ] **Step 3: `PartyServiceTest` 시그니처 정합화**

`src/test/kotlin/com/team2/server/party/application/service/PartyServiceTest.kt` 에서:
- `import com.team2.server.party.dto.CreatePaperOnlyPartyRequest` 제거 → `import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand` 추가
- `import com.team2.server.party.dto.CreateRealtimePartyRequest` 제거 → `import com.team2.server.party.application.dto.CreateRealtimePartyCommand` 추가
- 모든 `CreateRealtimePartyRequest(...)` → `CreateRealtimePartyCommand(...)` (동일 필드)
- 모든 `CreatePaperOnlyPartyRequest(...)` → `CreatePaperOnlyPartyCommand(...)` (동일 필드)
- `partyService.createRealtimeParty(userId, request)` 호출 시 두 번째 인자 → `command = command` (named arg)
- 반환 검증이 `CreatePartyResponse` 였다면 `Long` 으로 변경 (assertEquals(expected, partyService.createRealtimeParty(...)))

> 모든 변경은 시그니처 정합화. 테스트 의도는 동일.

- [ ] **Step 4: `CreatePartyUseCase.kt` 구현**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreatePartyUseCase(
    private val partyService: PartyService,
) {
    @Transactional
    fun createRealtime(userId: Long, command: CreateRealtimePartyCommand): Long =
        partyService.createRealtimeParty(userId = userId, command = command)

    @Transactional
    fun createPaperOnly(userId: Long, command: CreatePaperOnlyPartyCommand): Long =
        partyService.createPaperOnlyParty(userId = userId, command = command)
}
```

> `@Component` 사용 이유: `@Service` 는 PackageStructureTest.B3 룰에서 application.service 만 허용 — UseCase 클래스는 `@Component` 로 등록.

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.CreatePartyUseCaseTest"
```

Expected: BUILD SUCCESSFUL.

전체 빌드는 Controller compile RED 상태 — Task 7 에서 해소될 때까지 정상.

### Task 6-f: 신규 `DeletePartyUseCase` 도입

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/DeletePartyUseCase.kt`
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/DeletePartyUseCaseTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class DeletePartyUseCaseTest {
    @Mock
    lateinit var partyService: PartyService

    @InjectMocks
    lateinit var useCase: DeletePartyUseCase

    @Test
    fun `delete delegates to partyService`() {
        useCase.delete(partyId = 1L, userId = 42L)
        verify(partyService).deleteParty(partyId = 1L, userId = 42L)
    }
}
```

- [ ] **Step 2: `DeletePartyUseCase.kt` 구현**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DeletePartyUseCase(
    private val partyService: PartyService,
) {
    @Transactional
    fun delete(partyId: Long, userId: Long) {
        partyService.deleteParty(partyId = partyId, userId = userId)
    }
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.DeletePartyUseCaseTest"
```

Expected: PASS.

### Task 6-g: 신규 `ActivateInviteLinkUseCase` 도입

`PartyInviteService.activateInviteLink` 의 `@Transactional` 을 UseCase 로 이전.

**Files:**
- Create: `src/main/kotlin/com/team2/server/party/application/usecase/ActivateInviteLinkUseCase.kt`
- Modify: `src/main/kotlin/com/team2/server/party/application/service/PartyInviteService.kt` (어노테이션 제거 + 반환 타입 String 으로 단순화)
- Test: `src/test/kotlin/com/team2/server/party/application/usecase/ActivateInviteLinkUseCaseTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyInviteService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class ActivateInviteLinkUseCaseTest {
    @Mock
    lateinit var partyInviteService: PartyInviteService

    @InjectMocks
    lateinit var useCase: ActivateInviteLinkUseCase

    @Test
    fun `activate delegates and returns token`() {
        whenever(partyInviteService.activateInviteLink(partyId = 1L, userId = 42L))
            .thenReturn("example-token-0000")

        val token = useCase.activate(partyId = 1L, userId = 42L)

        assertEquals("example-token-0000", token)
        verify(partyInviteService).activateInviteLink(partyId = 1L, userId = 42L)
    }
}
```

- [ ] **Step 2: `PartyInviteService.activateInviteLink` 수정**

`src/main/kotlin/com/team2/server/party/application/service/PartyInviteService.kt`:
- 반환 타입 `ActivateInviteLinkResponse` → `String`
- 본문 끝 `return ActivateInviteLinkResponse(token = invite.token)` → `return invite.token`
- `@Transactional` 어노테이션 제거
- `import com.team2.server.party.dto.ActivateInviteLinkResponse` 제거
- `import org.springframework.transaction.annotation.Transactional` 제거

수정 후 `activateInviteLink` 시그니처:
```kotlin
fun activateInviteLink(partyId: Long, userId: Long): String { ... }
```

- [ ] **Step 3: `PartyInviteServiceTest` 시그니처 정합화**

`PartyInviteServiceTest.kt` 에서:
- `ActivateInviteLinkResponse` 관련 expected 가 있다면 `.token` 비교를 string 직접 비교로 변경
- `import` 정리

`grep -n "ActivateInviteLinkResponse" src/test/kotlin/com/team2/server/party/application/service/PartyInviteServiceTest.kt` 로 모든 참조 확인 후 정정.

- [ ] **Step 4: `ActivateInviteLinkUseCase.kt` 구현**

```kotlin
package com.team2.server.party.application.usecase

import com.team2.server.party.application.service.PartyInviteService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ActivateInviteLinkUseCase(
    private val partyInviteService: PartyInviteService,
) {
    @Transactional
    fun activate(partyId: Long, userId: Long): String =
        partyInviteService.activateInviteLink(partyId = partyId, userId = userId)
}
```

- [ ] **Step 5: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.party.application.usecase.ActivateInviteLinkUseCaseTest" \
              --tests "com.team2.server.party.application.service.PartyInviteServiceTest"
```

Expected: PASS.

### Task 6-h: 기존 UseCase 의 Service-side `@Transactional` 정리

기존 4개 UseCase 는 이미 `@Transactional` 을 본인 메서드에 가지고 있음 (`GetCharactersUseCase`, `GetUpcomingPartiesUseCase`, `JoinPartyInviteUseCase`, `LookupPartyInviteUseCase`). 본 PR 의 새 UseCase 3개도 `@Transactional` 부착 완료. Service 에서는 모두 제거됨.

확인:

```bash
grep -n "@Transactional" src/main/kotlin/com/team2/server/party/application/service/*.kt
```

Expected: 0 매치. (Task 6-e Step 2 / Task 6-g Step 2 결과)

```bash
grep -n "@Transactional" src/main/kotlin/com/team2/server/party/application/usecase/*.kt
```

Expected: `GetCharactersUseCase`(readOnly), `GetUpcomingPartiesUseCase`(readOnly), `JoinPartyInviteUseCase`, `LookupPartyInviteUseCase`(readOnly), `CreatePartyUseCase`(x2), `DeletePartyUseCase`, `ActivateInviteLinkUseCase` 에서 매치.

- [ ] **Step 1: Compile check (Controller 는 여전히 RED 가능)**

```bash
./gradlew compileKotlin compileTestKotlin
```

PartyController/PartyInviteController/PartyInviteLookupController/MePartyController 가 아직 Service / 이전 UseCase 시그니처를 사용 중이라면 RED. 다음 Task 7 에서 복구.

### Task 6 종합 Commit

위 6-a~6-h 를 분리 커밋해도 되고, 6-a 후 한 번 commit + 6-b~6-h 합쳐 한 번 commit (Controller 도 같이) 처리해도 무방.

권장 분기점:
- Commit A (Task 6-a 종료 후): "refactor: party usecase 를 application/usecase 로 이동"
- Commit B (Task 7 끝): "refactor: party UseCase 신설(Create/Delete/Activate) + Service @Transactional 제거 + Controller UseCase 직결"

이 plan 에서는 후자 분기점 사용. 6-b ~ 6-h 의 변경은 Task 7 commit 에 포함.

- [ ] **Step 1: 6-a 만 commit**

```bash
git add src/main/kotlin/com/team2/server/party/application/usecase \
        $(grep -rln "party\.application\.usecase" src/main/kotlin src/test/kotlin)
git commit -m "refactor: party usecase 를 application/usecase 로 이동"
```

---

## Task 7: API Layer — Controller 이동 + api/dto 분리 + 매핑

`party/controller/*` → `party/api/*`. `party/dto/{Request, Response, *Resolver 외}` → `party/api/dto/`. Controller 가 UseCase 만 호출하고 Request↔Command, Result↔Response 매핑을 수행하도록 변경.

> 본 Task 는 Task 6-b ~ 6-h 의 미해결 compile error 도 같이 해소.

**Files:**
- Move: 10개 컨트롤러
  - `party/controller/CharacterApi.kt` → `party/api/CharacterApi.kt`
  - `party/controller/CharacterController.kt` → `party/api/CharacterController.kt`
  - `party/controller/MePartyApi.kt` → `party/api/MePartyApi.kt`
  - `party/controller/MePartyController.kt` → `party/api/MePartyController.kt`
  - `party/controller/PartyApi.kt` → `party/api/PartyApi.kt`
  - `party/controller/PartyController.kt` → `party/api/PartyController.kt`
  - `party/controller/PartyInviteApi.kt` → `party/api/PartyInviteApi.kt`
  - `party/controller/PartyInviteController.kt` → `party/api/PartyInviteController.kt`
  - `party/controller/PartyInviteLookupApi.kt` → `party/api/PartyInviteLookupApi.kt`
  - `party/controller/PartyInviteLookupController.kt` → `party/api/PartyInviteLookupController.kt`
- Move: 8개 api/dto
  - `party/dto/ActivateInviteLinkResponse.kt` → `party/api/dto/ActivateInviteLinkResponse.kt`
  - `party/dto/CharacterResponse.kt` → `party/api/dto/CharacterResponse.kt`
  - `party/dto/CreatePaperOnlyPartyRequest.kt` → `party/api/dto/CreatePaperOnlyPartyRequest.kt`
  - `party/dto/CreatePartyResponse.kt` → `party/api/dto/CreatePartyResponse.kt`
  - `party/dto/CreateRealtimePartyRequest.kt` → `party/api/dto/CreateRealtimePartyRequest.kt`
  - `party/dto/PartyInviteLookupResponse.kt` → `party/api/dto/PartyInviteLookupResponse.kt`
  - `party/dto/PartyInviteParticipationResponse.kt` → `party/api/dto/PartyInviteParticipationResponse.kt`
  - `party/dto/UpcomingPartyResponse.kt` → `party/api/dto/UpcomingPartyResponse.kt`
- Move tests: 4개
  - `src/test/kotlin/com/team2/server/party/controller/CharacterControllerTest.kt` → `party/api/CharacterControllerTest.kt`
  - `party/controller/MePartyControllerTest.kt` → `party/api/MePartyControllerTest.kt`
  - `party/controller/PartyControllerTest.kt` → `party/api/PartyControllerTest.kt`
  - `party/controller/PartyInviteLookupControllerTest.kt` → `party/api/PartyInviteLookupControllerTest.kt`

- [ ] **Step 1: 디렉터리 + git mv (controller + api/dto)**

```bash
mkdir -p src/main/kotlin/com/team2/server/party/api/dto
mkdir -p src/test/kotlin/com/team2/server/party/api

# 컨트롤러 10개
for f in CharacterApi CharacterController MePartyApi MePartyController PartyApi PartyController PartyInviteApi PartyInviteController PartyInviteLookupApi PartyInviteLookupController; do
  git mv "src/main/kotlin/com/team2/server/party/controller/$f.kt" "src/main/kotlin/com/team2/server/party/api/$f.kt"
done

# api/dto 8개
for f in ActivateInviteLinkResponse CharacterResponse CreatePaperOnlyPartyRequest CreatePartyResponse CreateRealtimePartyRequest PartyInviteLookupResponse PartyInviteParticipationResponse UpcomingPartyResponse; do
  git mv "src/main/kotlin/com/team2/server/party/dto/$f.kt" "src/main/kotlin/com/team2/server/party/api/dto/$f.kt"
done

# test 4개
for f in CharacterControllerTest MePartyControllerTest PartyControllerTest PartyInviteLookupControllerTest; do
  git mv "src/test/kotlin/com/team2/server/party/controller/$f.kt" "src/test/kotlin/com/team2/server/party/api/$f.kt"
done
```

- [ ] **Step 2: package 갱신**

모든 이동 파일의 `package`:
- `package com.team2.server.party.controller` → `package com.team2.server.party.api`
- `package com.team2.server.party.dto` → `package com.team2.server.party.api.dto`

- [ ] **Step 3: 잔존 import 갱신 (party.controller / party.dto)**

```bash
grep -rln "com.team2.server.party.controller" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.controller|com.team2.server.party.api|g'

grep -rln "com.team2.server.party.dto" src/main/kotlin src/test/kotlin | \
  xargs sed -i '' 's|com\.team2\.server\.party\.dto|com.team2.server.party.api.dto|g'

grep -rn "com\.team2\.server\.party\.controller\|com\.team2\.server\.party\.dto[^.]" src/main/kotlin src/test/kotlin
```

마지막 expected: 0건. (`party.application.dto` 와 헷갈리지 않게 정규식 보정. 결과 검토)

- [ ] **Step 4: `PartyController` 가 UseCase 만 호출하도록 수정**

`src/main/kotlin/com/team2/server/party/api/PartyController.kt`:

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.exception.BusinessException
import com.team2.server.common.exception.ErrorCode
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.CreatePaperOnlyPartyRequest
import com.team2.server.party.api.dto.CreatePartyResponse
import com.team2.server.party.api.dto.CreateRealtimePartyRequest
import com.team2.server.party.application.dto.CreatePaperOnlyPartyCommand
import com.team2.server.party.application.dto.CreateRealtimePartyCommand
import com.team2.server.party.application.usecase.CreatePartyUseCase
import com.team2.server.party.application.usecase.DeletePartyUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyController(
    private val createPartyUseCase: CreatePartyUseCase,
    private val deletePartyUseCase: DeletePartyUseCase,
) : PartyApi {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/realtime")
    override fun createRealtimeParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateRealtimePartyRequest,
    ): ApiResponse<CreatePartyResponse> {
        val partyId = createPartyUseCase.createRealtime(
            userId = principal.userId,
            command = CreateRealtimePartyCommand(
                celebrantNickname = request.celebrantNickname,
                startedDate = request.startedDate,
                startTime = request.startTime,
                characterId = request.characterId,
            ),
        )
        return ApiResponse.success(HttpStatus.CREATED, CreatePartyResponse(partyId = partyId))
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/paper-only")
    override fun createPaperOnlyParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreatePaperOnlyPartyRequest,
    ): ApiResponse<CreatePartyResponse> {
        val partyId = createPartyUseCase.createPaperOnly(
            userId = principal.userId,
            command = CreatePaperOnlyPartyCommand(
                celebrantNickname = request.celebrantNickname,
                startedDate = request.startedDate,
            ),
        )
        return ApiResponse.success(HttpStatus.CREATED, CreatePartyResponse(partyId = partyId))
    }

    @PostMapping("/{partyType}")
    fun createPartyUnknownType(): Nothing = throw BusinessException(ErrorCode.RESOURCE_NOT_FOUND)

    @DeleteMapping("/{partyId}")
    override fun deleteParty(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<Unit> {
        deletePartyUseCase.delete(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(HttpStatus.OK, Unit)
    }
}
```

(주요 변경: `partyService` 의존 제거, `CreatePartyUseCase` / `DeletePartyUseCase` 의존, Request → Command 매핑 + 반환 Long → CreatePartyResponse 래핑)

- [ ] **Step 5: `PartyInviteController` UseCase 직결**

`src/main/kotlin/com/team2/server/party/api/PartyInviteController.kt`:

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.ActivateInviteLinkResponse
import com.team2.server.party.application.usecase.ActivateInviteLinkUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/parties")
class PartyInviteController(
    private val activateInviteLinkUseCase: ActivateInviteLinkUseCase,
) : PartyInviteApi {
    @PostMapping("/{partyId}/invite-link")
    override fun activateInviteLink(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable partyId: Long,
    ): ApiResponse<ActivateInviteLinkResponse> {
        val token = activateInviteLinkUseCase.activate(partyId = partyId, userId = principal.userId)
        return ApiResponse.success(ActivateInviteLinkResponse(token = token))
    }
}
```

- [ ] **Step 6: `PartyInviteLookupController` Result → Response 매핑**

`src/main/kotlin/com/team2/server/party/api/PartyInviteLookupController.kt`:

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.PartyInviteLookupResponse
import com.team2.server.party.api.dto.PartyInviteParticipationResponse
import com.team2.server.party.api.dto.RealtimeSchedule
import com.team2.server.party.application.dto.PartyInviteLookupResult
import com.team2.server.party.application.dto.RealtimeScheduleResult
import com.team2.server.party.application.usecase.JoinPartyInviteUseCase
import com.team2.server.party.application.usecase.LookupPartyInviteUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/party-invites")
class PartyInviteLookupController(
    private val lookupPartyInviteUseCase: LookupPartyInviteUseCase,
    private val joinPartyInviteUseCase: JoinPartyInviteUseCase,
) : PartyInviteLookupApi {
    @GetMapping("/{inviteToken}")
    override fun getPartyInvite(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable inviteToken: String,
    ): ApiResponse<PartyInviteLookupResponse> {
        val result = lookupPartyInviteUseCase.lookup(inviteToken, principal?.userId)
        return ApiResponse.success(result.toResponse())
    }

    @PostMapping("/{inviteToken}/participants/me")
    override fun joinPartyInvite(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteToken: String,
    ): ApiResponse<PartyInviteParticipationResponse> {
        val participantId = joinPartyInviteUseCase.join(inviteToken, principal.userId)
        return ApiResponse.success(PartyInviteParticipationResponse(participantId = participantId))
    }

    private fun PartyInviteLookupResult.toResponse(): PartyInviteLookupResponse =
        PartyInviteLookupResponse(
            partyId = partyId,
            celebrantNickname = celebrantNickname,
            isHost = isHost,
            partyOption = partyOption,
            partyEnded = partyEnded,
            rollingPaperWritten = rollingPaperWritten,
            partyStartDate = partyStartDate,
            partyEndDate = partyEndDate,
            realtimeSchedule = realtimeSchedule?.toResponse(),
        )

    private fun RealtimeScheduleResult.toResponse(): RealtimeSchedule =
        RealtimeSchedule(
            liveStartAt = liveStartAt,
            enterableFrom = enterableFrom,
            liveEndAt = liveEndAt,
            liveDurationMinutes = liveDurationMinutes,
        )
}
```

- [ ] **Step 7: `MePartyController` Result → Response 매핑**

`src/main/kotlin/com/team2/server/party/api/MePartyController.kt`:

```kotlin
package com.team2.server.party.api

import com.team2.server.auth.principal.UserPrincipal
import com.team2.server.common.web.ApiResponse
import com.team2.server.party.api.dto.UpcomingPartyResponse
import com.team2.server.party.api.dto.UpcomingRealtimeScheduleResponse
import com.team2.server.party.application.dto.UpcomingPartyResult
import com.team2.server.party.application.dto.UpcomingRealtimeScheduleResult
import com.team2.server.party.application.usecase.GetUpcomingPartiesUseCase
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
class MePartyController(
    private val getUpcomingPartiesUseCase: GetUpcomingPartiesUseCase,
) : MePartyApi {
    @GetMapping("/upcoming-parties")
    override fun getUpcomingParties(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ApiResponse<List<UpcomingPartyResponse>> {
        val results = getUpcomingPartiesUseCase.getUpcomingParties(principal.userId)
        return ApiResponse.success(results.map { it.toResponse() })
    }

    private fun UpcomingPartyResult.toResponse(): UpcomingPartyResponse =
        UpcomingPartyResponse(
            partyId = partyId,
            inviteToken = inviteToken,
            partyOption = partyOption,
            celebrantNickname = celebrantNickname,
            partyStartedAt = partyStartedAt,
            partyEndedAt = partyEndedAt,
            isHost = isHost,
            rollingPaperWritten = rollingPaperWritten,
            hostRollingPaperOpenAt = hostRollingPaperOpenAt,
            realtimeSchedule = realtimeSchedule?.toResponse(),
        )

    private fun UpcomingRealtimeScheduleResult.toResponse(): UpcomingRealtimeScheduleResponse =
        UpcomingRealtimeScheduleResponse(
            enterableFrom = enterableFrom,
            liveStartAt = liveStartAt,
            liveEndAt = liveEndAt,
        )
}
```

확정 정책:

- 다가오는 파티는 `party.startedAt ASC`, `participant.createdAt DESC`, `participant.id DESC` 순으로 정렬한다.
- REALTIME과 PAPER_ONLY 파티 생성 시 기본 초대 토큰을 함께 생성한다.
- 종료 전 파티 중 유효 초대 토큰이 없는 파티는 Flyway 마이그레이션으로 생성한다.
- 다가오는 파티 응답은 유효한 `inviteToken`을 반환한다.

- [ ] **Step 8: `CharacterController` 매핑 확인**

이미 `getCharactersUseCase.invoke().map(CharacterResponse::from)` 패턴 — 변경 없음. 단 `CharacterResponse.from(result: CharacterResult)` 의 `CharacterResult` import 가 `party.application.dto.CharacterResult` 로 정상 갱신되었는지 확인.

```bash
grep -n "CharacterResult" src/main/kotlin/com/team2/server/party/api/dto/CharacterResponse.kt
```

Expected: `import com.team2.server.party.application.dto.CharacterResult`.

- [ ] **Step 9: 컨트롤러 테스트 정합화**

- `PartyControllerTest` — mock 대상이 `partyService` → `createPartyUseCase` / `deletePartyUseCase` 로 변경. Request/Response 매핑 검증은 그대로 — 단 service 대신 useCase 의 메서드를 verify.
- `PartyInviteLookupControllerTest` — UseCase 의 반환 타입이 Result 로 변경됐으므로 mock setup 갱신.
- `MePartyControllerTest` — `GetUpcomingPartiesUseCase` 반환이 `List<UpcomingPartyResult>` 로 변경.
- `CharacterControllerTest` — 변경 사항 거의 없음.

각 테스트 파일을 열어 import 정합화 + mock when/then 의 반환 타입 정합화. 검증 로직(JSON response 매칭)은 변경 없어야 한다.

- [ ] **Step 10: 전체 build + test**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. 모든 테스트 통과.

만약 FAIL 이면 어떤 테스트가 실패했는지 출력을 확인하고 정합화 작업 보강.

- [ ] **Step 11: Commit (Task 6-b~6-h + Task 7 묶음)**

```bash
git add src/main/kotlin/com/team2/server/party \
        src/test/kotlin/com/team2/server/party
git status --short
git commit -m "refactor: party UseCase 신설(Create/Delete/Activate) + Controller UseCase 직결 + DTO 분리"
```

(`git status --short` 결과에서 stage 되지 않은 항목 확인)

---

## Task 8: chat PR 7 의존 항목 Service 사전 보강

스펙 §3-5 의 (c) 에서 정의된 메서드를 PR 3 에 추가. 호출자는 PR 7 에서 정렬되므로 본 PR 에서는 정의 + 단위 테스트만.

**Files:**
- Modify: `src/main/kotlin/com/team2/server/party/application/service/PartyService.kt` — `findActiveRealtimeParty(partyId)` 추가
- Modify: `src/main/kotlin/com/team2/server/party/application/service/ParticipantService.kt` — `findOrCreate(party, userId)` 추가
- Modify: `src/main/kotlin/com/team2/server/party/application/service/PartyInviteService.kt` — `resolveEnterableRealtimeInvite(token, now)` 추가
- Test:
  - `src/test/kotlin/com/team2/server/party/application/service/PartyServiceTest.kt` — `findActiveRealtimeParty` 테스트 추가
  - `src/test/kotlin/com/team2/server/party/application/service/ParticipantServiceTest.kt` (신규) — `findOrCreate` 테스트
  - `src/test/kotlin/com/team2/server/party/application/service/PartyInviteServiceTest.kt` — `resolveEnterableRealtimeInvite` 테스트 추가

### Task 8-a: `PartyService.findActiveRealtimeParty`

chat 의 `SendChatMessageUseCase` 가 사용하는 로직을 추출.

- [ ] **Step 1: PartyServiceTest 에 케이스 추가**

`PartyServiceTest.kt` 끝에 다음 테스트 추가:

```kotlin
@Test
fun `findActiveRealtimeParty returns realtime party when live open`() {
    val realtimeParty = RealtimeParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now().minusMinutes(1),
    )
    whenever(partyRepository.findPartyById(10L)).thenReturn(realtimeParty)

    val result = partyService.findActiveRealtimeParty(10L)

    assertEquals(realtimeParty, result)
}

@Test
fun `findActiveRealtimeParty throws PARTY_NOT_FOUND when party absent`() {
    whenever(partyRepository.findPartyById(10L)).thenReturn(null)
    val e = assertThrows<BusinessException> { partyService.findActiveRealtimeParty(10L) }
    assertEquals(ErrorCode.PARTY_NOT_FOUND, e.errorCode)
}

@Test
fun `findActiveRealtimeParty throws CHAT_NOT_SUPPORTED when paper only`() {
    val paperOnly = PaperOnlyParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now(),
    )
    whenever(partyRepository.findPartyById(10L)).thenReturn(paperOnly)
    val e = assertThrows<BusinessException> { partyService.findActiveRealtimeParty(10L) }
    assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, e.errorCode)
}

@Test
fun `findActiveRealtimeParty throws CHAT_NOT_ACTIVE when not in live window`() {
    val realtimeParty = RealtimeParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now().plusHours(1),
    )
    whenever(partyRepository.findPartyById(10L)).thenReturn(realtimeParty)
    val e = assertThrows<BusinessException> { partyService.findActiveRealtimeParty(10L) }
    assertEquals(ErrorCode.CHAT_NOT_ACTIVE, e.errorCode)
}
```

> 필요 import 추가: `com.team2.server.party.domain.entity.RealtimeParty`, `com.team2.server.party.domain.entity.PaperOnlyParty`, `java.time.LocalDateTime`, `kotlin.test.assertEquals`, `org.junit.jupiter.api.assertThrows`, `org.mockito.kotlin.whenever`.

- [ ] **Step 2: `BusinessException.errorCode` 접근 확인**

```bash
grep -n "errorCode" src/main/kotlin/com/team2/server/common/exception/BusinessException.kt
```

Expected: `val errorCode: ErrorCode` 가 public 으로 노출. 만약 아니면 위 테스트에서는 `assertThrows` 만 사용하고 `errorCode` 비교는 생략.

- [ ] **Step 3: PartyService 에 메서드 추가**

`PartyService.kt` 끝 (private 메서드 위)에 추가:

```kotlin
fun findActiveRealtimeParty(partyId: Long): RealtimeParty {
    val party = partyRepository.findPartyById(partyId)
        ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
    if (party.partyOption != PartyOption.REALTIME) {
        throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
    }
    val realtimeParty = org.hibernate.Hibernate.unproxy(party) as RealtimeParty
    if (realtimeParty.status() != RealtimePartyStatus.LIVE_OPEN) {
        throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
    }
    return realtimeParty
}
```

> 필요 import: `com.team2.server.party.domain.entity.PartyOption`, `com.team2.server.party.domain.entity.RealtimePartyStatus`. (`RealtimeParty` 는 이미 import 됨)
>
> `org.hibernate.Hibernate` 는 파일 상단 import 또는 fully-qualified 사용. 위 예시는 fully-qualified.

- [ ] **Step 4: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.party.application.service.PartyServiceTest"
```

Expected: PASS (기존 케이스 + 추가 4개 모두).

### Task 8-b: `ParticipantService.findOrCreate`

chat 의 `EnterRealtimePartyUseCase` 가 사용할 메서드.

- [ ] **Step 1: ParticipantServiceTest 신규 생성**

`src/test/kotlin/com/team2/server/party/application/service/ParticipantServiceTest.kt`:

```kotlin
package com.team2.server.party.application.service

import com.team2.server.party.domain.entity.Participant
import com.team2.server.party.domain.entity.PaperOnlyParty
import com.team2.server.party.infrastructure.persistence.ParticipantRepository
import com.team2.server.user.entity.AuthProvider
import com.team2.server.user.entity.User
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class ParticipantServiceTest {
    @Mock
    lateinit var participantRepository: ParticipantRepository

    @InjectMocks
    lateinit var service: ParticipantService

    private val sampleUser = User(
        email = "x@x",
        providerId = "123",
        provider = AuthProvider.KAKAO,
    )

    private val sampleParty = PaperOnlyParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now().plusDays(1),
    )

    @Test
    fun `findOrCreate returns existing participant when present`() {
        val existing = Participant(party = sampleParty, user = sampleUser)
        whenever(participantRepository.findByPartyIdAndUserId(sampleParty.id, 42L)).thenReturn(existing)

        val result = service.findOrCreate(sampleParty, userId = 42L, user = sampleUser)

        assertEquals(existing, result)
    }

    @Test
    fun `findOrCreate saves new participant when absent`() {
        whenever(participantRepository.findByPartyIdAndUserId(sampleParty.id, 42L)).thenReturn(null)
        whenever(participantRepository.save(any<Participant>())).thenAnswer { it.arguments[0] }

        service.findOrCreate(sampleParty, userId = 42L, user = sampleUser)

        verify(participantRepository).save(any<Participant>())
    }
}
```

> `User` 생성자가 다를 수 있음 — 실제 `user/entity/User.kt` 의 생성자 시그니처에 맞춰 조정.

- [ ] **Step 2: ParticipantService 에 메서드 추가**

`ParticipantService.kt` 에 추가:

```kotlin
import com.team2.server.party.infrastructure.persistence.ParticipantRepository  // 이미 있을 것
import com.team2.server.user.entity.User

fun findOrCreate(party: Party, userId: Long, user: User): Participant =
    participantRepository.findByPartyIdAndUserId(party.id, userId)
        ?: participantRepository.save(Participant(party = party, user = user))
```

> 기존 `joinMember/joinAnonymous` 와 의도 차이: `findOrCreate` 는 unique constraint 충돌 처리 없음 (chat enter 흐름은 동시성 충돌 가능성이 낮은 read-after-find 패턴). PR 7 에서 chat 의 실제 사용 패턴을 확인하고 정합화.

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.party.application.service.ParticipantServiceTest"
```

Expected: PASS.

### Task 8-c: `PartyInviteService.resolveEnterableRealtimeInvite`

chat 의 `EnterRealtimePartyUseCase.validateInvite + validateEnterable` 로직 추출.

- [ ] **Step 1: PartyInviteServiceTest 에 케이스 추가**

`PartyInviteServiceTest.kt` 에 다음 케이스 추가 (기존 패턴 따라):

```kotlin
@Test
fun `resolveEnterableRealtimeInvite returns invite when realtime live window`() {
    val realtimeParty = RealtimeParty(
        ownerId = 1L,
        celebrantNickname = "홍길동",
        startedAt = LocalDateTime.now(),
    )
    val invite = PartyInvite(
        party = realtimeParty,
        token = "tok",
        expiresAt = LocalDateTime.now().plusMinutes(30),
    )
    whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)

    val result = partyInviteService.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())

    assertEquals(invite, result)
}

@Test
fun `resolveEnterableRealtimeInvite throws PARTY_NOT_FOUND when invite absent`() {
    whenever(partyInviteRepository.findByToken("tok")).thenReturn(null)
    val e = assertThrows<BusinessException> {
        partyInviteService.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
    }
    assertEquals(ErrorCode.PARTY_NOT_FOUND, e.errorCode)
}

@Test
fun `resolveEnterableRealtimeInvite throws INVITE_LINK_EXPIRED when expired`() {
    val invite = PartyInvite(
        party = RealtimeParty(ownerId = 1L, celebrantNickname = "x", startedAt = LocalDateTime.now()),
        token = "tok",
        expiresAt = LocalDateTime.now().minusMinutes(1),
    )
    whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
    val e = assertThrows<BusinessException> {
        partyInviteService.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
    }
    assertEquals(ErrorCode.INVITE_LINK_EXPIRED, e.errorCode)
}

@Test
fun `resolveEnterableRealtimeInvite throws CHAT_NOT_SUPPORTED when paper only`() {
    val invite = PartyInvite(
        party = PaperOnlyParty(ownerId = 1L, celebrantNickname = "x", startedAt = LocalDateTime.now().plusDays(1)),
        token = "tok",
        expiresAt = LocalDateTime.now().plusDays(7),
    )
    whenever(partyInviteRepository.findByToken("tok")).thenReturn(invite)
    val e = assertThrows<BusinessException> {
        partyInviteService.resolveEnterableRealtimeInvite("tok", LocalDateTime.now())
    }
    assertEquals(ErrorCode.CHAT_NOT_SUPPORTED, e.errorCode)
}
```

> 필요 import 추가: `RealtimeParty`, `PaperOnlyParty`, `PartyInvite`, `BusinessException`, `ErrorCode`, `LocalDateTime`, `assertThrows`, `assertEquals`, `whenever`.

- [ ] **Step 2: PartyInviteService 에 메서드 추가**

`PartyInviteService.kt` 에 추가 (기존 메서드 아래, private 위):

```kotlin
import com.team2.server.party.domain.entity.PartyOption
import com.team2.server.party.domain.entity.RealtimeParty

fun resolveEnterableRealtimeInvite(inviteToken: String, now: LocalDateTime): PartyInvite {
    val invite = partyInviteRepository.findByToken(inviteToken)
        ?: throw BusinessException(ErrorCode.PARTY_NOT_FOUND)
    if (!invite.expiresAt.isAfter(now)) {
        throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
    }
    val party = invite.party
    if (party.partyOption != PartyOption.REALTIME) {
        throw BusinessException(ErrorCode.CHAT_NOT_SUPPORTED)
    }
    val realtimeParty = org.hibernate.Hibernate.unproxy(party) as RealtimeParty
    val enterableFrom = realtimeParty.startedAt.minusMinutes(RealtimeParty.ENTERABLE_BEFORE_MINUTES)
    val enterableTo = realtimeParty.startedAt.plusMinutes(RealtimeParty.LIVE_DURATION_MINUTES)
    if (now.isBefore(enterableFrom) || !now.isBefore(enterableTo)) {
        throw BusinessException(ErrorCode.CHAT_NOT_ACTIVE)
    }
    return invite
}
```

- [ ] **Step 3: 테스트 실행**

```bash
./gradlew test --tests "com.team2.server.party.application.service.PartyInviteServiceTest"
```

Expected: PASS (기존 + 신규 케이스).

### Task 8 Commit

- [ ] **Step 1: 전체 빌드 확인**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/com/team2/server/party/application/service \
        src/test/kotlin/com/team2/server/party/application/service
git commit -m "feat: chat PR 7 cross-feature 정렬용 party Service 메서드 사전 추가"
```

---

## Task 9: 빈 디렉터리 정리 + 최종 빌드

이동 후 남은 빈 디렉터리 제거.

- [ ] **Step 1: 빈 디렉터리 확인**

```bash
find src/main/kotlin/com/team2/server/party -type d -empty
find src/test/kotlin/com/team2/server/party -type d -empty
```

Expected:
- `party/controller`, `party/dto`, `party/entity`, `party/repository`, `party/service`, `party/usecase` (그리고 test 의 동일 디렉터리)

- [ ] **Step 2: 빈 디렉터리 제거**

```bash
rmdir src/main/kotlin/com/team2/server/party/controller \
      src/main/kotlin/com/team2/server/party/dto \
      src/main/kotlin/com/team2/server/party/entity \
      src/main/kotlin/com/team2/server/party/repository \
      src/main/kotlin/com/team2/server/party/service \
      src/main/kotlin/com/team2/server/party/usecase

rmdir src/test/kotlin/com/team2/server/party/controller \
      src/test/kotlin/com/team2/server/party/entity \
      src/test/kotlin/com/team2/server/party/repository \
      src/test/kotlin/com/team2/server/party/service \
      src/test/kotlin/com/team2/server/party/usecase 2>/dev/null || true
```

(`2>/dev/null || true` 는 일부 디렉터리가 이미 없을 수 있는 케이스 대비)

- [ ] **Step 3: 최종 빌드**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. 모든 테스트 통과.

- [ ] **Step 4: ArchUnit 룰 변경 없음 확인**

```bash
./gradlew test --tests "com.team2.server.architecture.*"
```

Expected: 기존 활성 룰(A4, A5, H1, X1, X2)만 RUNNING 상태 + 모두 PASS. PR 3 에서는 새 룰 활성 없음 (스펙 §4 rev.2 에 따라 D/A1~3/B/C/E 는 PR 8 일괄).

- [ ] **Step 5: Commit (필요 시)**

```bash
git status --short
# 빈 디렉터리 rmdir 만으로 tracking 변경이 없으면 commit 불필요.
# 그 외 잔여 변경이 있으면:
git add -u
git commit -m "chore: party 빈 디렉터리 정리"
```

---

## Task 10: PR 본문 작성 (선택)

`.github/PULL_REQUEST_TEMPLATE.md` 가 있으면 그 형식에 맞춰. 핵심 포인트:
- PR 3 / 스펙 링크
- 이동된 파일 수 (entity 7, repository 5, service 3, usecase 4+3 신규, controller 10, api/dto 8, application/dto 5 신규)
- 새 UseCase 3종 (CreatePartyUseCase / DeletePartyUseCase / ActivateInviteLinkUseCase)
- chat PR 7 prep: PartyService.findActiveRealtimeParty, ParticipantService.findOrCreate, PartyInviteService.resolveEnterableRealtimeInvite
- ArchUnit 룰 활성 변경 없음
- 테스트: PartyService/PartyInviteService/ParticipantService/UseCase 테스트 추가 또는 갱신
- `./gradlew build` GREEN

---

## Self-Review 체크포인트

- [ ] 스펙 §2-4 (party 매핑)의 모든 항목이 Task 로 다뤄짐
  - entity 7 → Task 1 ✓
  - repository 5 → Task 2 ✓
  - service 3 → Task 4 ✓
  - usecase 4 → Task 6-a ✓
  - controller 10 → Task 7 ✓
  - dto 분리 → Task 5 (Command/Result) + Task 7 (Response) ✓
  - CharacterImageUrlResolver → Task 3 (rename + move) ✓
  - 신규 UseCase: CreatePartyUseCase/ActivateInviteLinkUseCase → Task 6-e/6-g ✓
  - 신규 DTO: CreatePaperOnlyPartyCommand/CreateRealtimePartyCommand/PartyInviteLookupResult/RealtimeScheduleResult → Task 5 ✓
- [ ] 스펙 §3-1 `@Transactional` Service → UseCase 이전: PartyService(3) + PartyInviteService(1) 4개 모두 제거됨 → Task 6-e/6-g/6-h 에서 확인 ✓
- [ ] 스펙 §3-2 Controller → UseCase only: PartyController/PartyInviteController/PartyInviteLookupController/MePartyController 모두 정렬 → Task 7 ✓
- [ ] 스펙 §5 PR 3 의 "Service 보강 (chat PR 7 의존 항목)": findActiveRealtimeParty / findOrCreate / resolveEnterableRealtimeInvite → Task 8 ✓
- [ ] 스펙에 명시되지 않았지만 필수 항목 추가:
  - DeletePartyUseCase (Task 6-f) — Controller → UseCase only 충족 위해 필수
  - UpcomingPartyResult + UpcomingRealtimeScheduleResult (Task 5) — UseCase 가 api/dto 의존 제거 위해 필수
  - JoinPartyInviteUseCase 반환 primitive 화 (Task 6-d) — 동일 이유
- [ ] 테스트 동기 이동 (스펙 §6 위험표): 모든 test 파일 git mv 처리 → Task 1/2/4/7 ✓
- [ ] cross-feature import 갱신 (chat/rollingpaper): Task 1 Step 4 + Task 2 Step 3 + Task 4 Step 3 ✓
- [ ] ArchUnit 룰 활성 변경 없음 (PR 8 일괄): Task 9 Step 4 ✓

---

## 종합 Commit 시퀀스 (참고)

본 plan 을 진행하면 develop 기준 PR 브랜치에 7~8 개 commit 이 쌓인다:

1. `refactor: party entity 를 domain/entity 로 이동` (Task 1)
2. `refactor: party repository 를 infrastructure/persistence 로 이동 + repository @Transactional 제거` (Task 2)
3. `refactor: CharacterImageUrlResolver 를 infrastructure 로 이동하고 CharacterImageResolver 로 리네임` (Task 3)
4. `refactor: party service 를 application/service 로 이동` (Task 4)
5. `feat: party application/dto 에 Command/Result 타입 도입` (Task 5)
6. `refactor: party usecase 를 application/usecase 로 이동` (Task 6-a)
7. `refactor: party UseCase 신설(Create/Delete/Activate) + Controller UseCase 직결 + DTO 분리` (Task 6-b~h + Task 7)
8. `feat: chat PR 7 cross-feature 정렬용 party Service 메서드 사전 추가` (Task 8)
9. (선택) `chore: party 빈 디렉터리 정리` (Task 9)

---

## 실행 시작 전 최종 점검

- 작업 브랜치: `refactor/pr3-party-layered` (또는 팀 컨벤션에 맞는 이름)
- develop 최신화: `git fetch origin && git rebase origin/develop`
- baseline build GREEN
- 본 plan 의 Task 0 부터 순차 진행

각 Task 종료 시 빌드 + 테스트 GREEN 유지가 원칙. RED 상태로 다음 Task 시작 금지 (단 Task 6-b ~ 6-h 의 Controller 미수정 RED 는 Task 7 와 묶음으로 처리하는 의도된 RED 구간).
