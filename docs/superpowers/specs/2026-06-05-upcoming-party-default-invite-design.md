# 다가오는 파티 정렬 및 기본 초대 토큰 정책

- 작성일: 2026-06-05
- 대상 API: `GET /api/v1/me/upcoming-parties`

## 1. 다가오는 파티 정렬

다가오는 파티는 사용자가 참여 중이며 아직 종료되지 않은 파티를 파티 시작 시각이 가까운 순으로 반환한다.

정렬 기준:

1. `party.startedAt ASC`
2. 시작 시각이 같으면 `participant.createdAt DESC`
3. 참여 시각도 같으면 `participant.id DESC`

## 2. 기본 초대 토큰

REALTIME과 PAPER_ONLY 파티 모두 생성 트랜잭션에서 기본 초대 토큰을 함께 생성한다.

- 생성 유스케이스가 파티 생성 후 `PartyInviteService.activateInviteLink(...)`를 호출한다.
- 유효 토큰이 있으면 재사용하고, 없으면 새 토큰을 생성한다.
- 토큰 만료 시각은 `Party.endedAt()`과 같다.
- 다가오는 파티 응답은 해당 파티의 유효 초대 토큰을 `inviteToken`으로 반환한다.
- 종료 전 파티의 `inviteToken`은 유효한 토큰이다.

Flyway 마이그레이션으로 종료 전 파티의 기본 초대 토큰을 생성한다.

- 종료 전 파티 중 유효 초대 토큰이 없는 파티가 대상이다.
- 토큰 만료 시각은 `Party.endedAt()`과 같다.

## 3. 공유 동작

다가오는 파티 카드의 링크 복사는 응답의 `inviteToken`으로 공유 링크를 만든다.

`POST /api/v1/parties/{partyId}/invite-link`는 유효 토큰을 반환하고, 유효 토큰이 없으면 새로 생성한다.
