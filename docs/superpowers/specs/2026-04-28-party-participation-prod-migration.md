# 파티 참여 — Prod DDL 마이그레이션 메모

prod 환경(`ddl-auto: validate`)에서는 `participant` 테이블 스키마 변경이 자동 반영되지 않는다. 운영 배포 전 다음 DDL을 수동(또는 마이그레이션 도구) 실행한다.

## 변경 사항

1. `participant.character_id` nullable 허용.
2. 회원 중복 참여 방지를 위한 `(party_id, user_id)` 복합 unique 제약 추가.

## 적용 SQL

```sql
ALTER TABLE participant MODIFY COLUMN character_id BIGINT NULL;

ALTER TABLE participant
ADD CONSTRAINT uk_participant_party_user UNIQUE (party_id, user_id);
```

## 주의 사항

- MySQL 기준 unique 제약은 `user_id IS NULL`인 row를 여러 건 허용하므로 비회원 참여에는 영향이 없다.
- 운영 DB에 이미 중복된 `(party_id, user_id)` 데이터가 있으면 unique 제약 추가가 실패하므로 사전 정리 후 적용한다.
- Flyway/Liquibase 도입 시 본 SQL을 마이그레이션 파일로 변환한다.
