# 파티 참여 도메인 정리 및 API 보류 설계

## 현재 상태

파티 조회 API와 파티 참여 API는 기획 변경 가능성이 커서 현재 코드에서 제거한다.

제거된 API:

```http
GET /api/v1/parties/{inviteToken}
POST /api/v1/parties/{inviteToken}/participants
```

현재 유지되는 API:

```http
POST /api/v1/parties/{partyOption}
POST /api/v1/parties/{partyId}/invite-link
GET /api/v1/characters
```

`POST /api/v1/parties/{partyId}/invite-link`는 현재 토큰 발급/재사용 API만 유지한다. 토큰을 소비하던 파티 조회/참여 API는 제거됐으므로, 프론트에서 초대 토큰을 사용하는 경로는 새 기획 확정 후 다시 연결해야 한다.

운영 prod 설정은 DB schema `validate` 기준이다. 현재 변경은 엔티티 기준 도메인 정리이며, 운영 DB migration 적용 여부와 시점은 아직 이 문서 범위에서 다루지 않는다.

## 유지하는 도메인 변경

API는 제거하지만, 참여자 모델은 이후 새 참여 플로우를 수용할 수 있도록 정리한다.

### Participant

`participant`는 파티에 참여한 주체를 나타낸다.

| 필드 | 설명 |
|------|------|
| `party_id` | 참여한 파티 |
| `user_id` | 회원 참여자 ID. 비회원이면 `null` |
| `is_celebrant` | 주인공 여부 |
| `has_written_paper` | 롤링페이퍼 작성 여부 |

제약:

- `(party_id, user_id)` unique: 회원 중복 참여 방지

비회원은 별도 guest 테이블 없이 파티 안의 `participant` 한 건으로만 표현한다. 현재 요구사항에서는 롤링페이퍼 작성 후 수정/삭제가 불가능하므로, 비회원 브라우저 식별 토큰을 장기 관리할 필요가 없다.

`role` 컬럼은 두지 않는다. 현재 도메인에서는 파티 생성자는 `party.owner_id`, 주인공은 `participant.is_celebrant`로 구분한다.

### RealtimeParticipantProfile

`realtime_participant_profile`은 참여자의 실시간 표시 프로필을 저장한다.

| 필드 | 설명 |
|------|------|
| `participant_id` | 참여자 ID. 1:1 unique |
| `nickname` | 표시 닉네임 |
| `character_id` | 선택한 캐릭터. 채팅 비허용 플로우에서는 `null` 가능 |

파티 생성 시 생성자 참여자에 대해 기본 프로필을 함께 생성한다.

### RollingPaper

`rolling_paper.writer_participant_id`는 작성자 참여자를 참조한다.

작성 당시 표시명을 고정할 수 있도록 `writer_nickname` 스냅샷 컬럼을 둔다. 새 롤링페이퍼 작성 API에서 최신 프로필명을 따라갈지, 작성 당시 닉네임을 고정할지 정책을 확정해야 한다.

## Security

현재 공개 허용 경로:

```kotlin
auth.requestMatchers(HttpMethod.GET, "/api/v1/characters").permitAll()
auth.requestMatchers(HttpMethod.GET, "/images/**").permitAll()
```

파티 생성과 초대링크 활성화는 인증이 필요하다.

## 재설계 시 확인할 것

- 파티 조회 화면에서 필요한 정보와 인증 정책
- 비회원 중복 참여 판단 범위
- `RealtimeParticipantProfile` 생성 시점
- 롤링페이퍼 작성자 닉네임을 스냅샷으로 고정할지 여부
- 캐릭터 선택이 어떤 파티 옵션에서 필요한지
