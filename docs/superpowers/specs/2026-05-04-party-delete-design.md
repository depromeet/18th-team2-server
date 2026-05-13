# 파티 삭제 API 설계

## 개요

파티 주최자가 파티 시작 전에 파티를 삭제할 수 있는 API를 구현한다.
연관 데이터(Participant, PartyInvite, RealtimeParticipantProfile)를 Service 레이어에서 순서대로 하드 딜리트한다.

## API

```
DELETE /api/v1/parties/{partyId}
```

- 인증 필요 (`@AuthenticationPrincipal UserPrincipal`)
- 성공 응답: `204 No Content`
- `PartyController`에 추가

## 검증 로직

`PartyService.deleteParty(partyId: Long, userId: Long)` 내에서 순서대로 검증한다.

| 순서 | 조건 | 에러 코드 |
|------|------|-----------|
| 1 | 파티 존재 여부 | `PARTY_NOT_FOUND` |
| 2 | `party.ownerId == userId` | `PARTY_FORBIDDEN` |
| 3 | `now < party.startedAt` | `PARTY_ALREADY_STARTED` (신규) |

`PARTY_ALREADY_STARTED`는 기존 `ErrorCode` enum에 추가한다.

## 삭제 순서

외래키 의존성 역순으로 삭제한다.

1. `RealtimeParticipantProfile` — 해당 파티 참여자 ID 목록으로 일괄 삭제
2. `Participant` — `partyId`로 일괄 삭제
3. `PartyInvite` — `partyId`로 일괄 삭제
4. `Party` — 삭제

## Repository 변경사항

기존 Repository에 JPA 쿼리 메서드를 추가한다.

```kotlin
// ParticipantRepository
fun findAllByPartyId(partyId: Long): List<Participant>
fun deleteAllByPartyId(partyId: Long)

// PartyInviteRepository
fun deleteAllByPartyId(partyId: Long)

// RealtimeParticipantProfileRepository
fun deleteAllByParticipantIdIn(participantIds: List<Long>)
```

## 에러 코드

`ErrorCode` enum에 추가:

```kotlin
PARTY_ALREADY_STARTED(HttpStatus.CONFLICT, "파티가 이미 시작되었습니다.")
```

## 테스트 케이스

| 케이스 | 결과 |
|--------|------|
| 인증 없이 요청 | 401 |
| 존재하지 않는 파티 | PARTY_NOT_FOUND |
| 주최자가 아닌 유저 요청 | PARTY_FORBIDDEN |
| 파티 시작 후 삭제 시도 | PARTY_ALREADY_STARTED |
| 파티 시작 전 주최자 요청 | 204 + 연관 데이터 전체 삭제 확인 |
