# 카카오 OAuth 로그인 — Prod DDL 마이그레이션 메모

prod 환경(`ddl-auto: validate`)에서는 `users` 테이블에 자동 변경이 적용되지 않는다. 운영 배포 전 다음 DDL을 수동(또는 마이그레이션 도구) 실행한다.

## 변경 사항

1. `provider_id` 컬럼 추가 (`VARCHAR(100) NOT NULL`).
2. `(provider, provider_id)` 복합 unique 제약 추가.

## 기존 데이터 처리

기존 `users` 행이 있을 경우 `provider_id` 컬럼에 NOT NULL 추가는 즉시 실패한다. 다음 순서로 처리:

```sql
-- 1) nullable 컬럼 추가
ALTER TABLE users ADD COLUMN provider_id VARCHAR(100) NULL;

-- 2) 기존 데이터 백필 (provider별로 별도 작업, 신규 서비스라면 TRUNCATE 가능)
UPDATE users SET provider_id = CAST(id AS CHAR) WHERE provider_id IS NULL;

-- 3) NOT NULL 적용
ALTER TABLE users MODIFY COLUMN provider_id VARCHAR(100) NOT NULL;

-- 4) 복합 unique 제약
ALTER TABLE users ADD CONSTRAINT uk_users_provider_provider_id UNIQUE (provider, provider_id);
```

## 후속 PR 작업

- Flyway/Liquibase 도입 여부 결정 → 도입 시 본 SQL을 마이그레이션 파일로 변환.
- 운영 배포 직전 DBA와 협의해 적용 시점 결정.
