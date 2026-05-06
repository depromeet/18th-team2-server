# Flyway Migration Design

- 작성일: 2026-05-06
- 기준 브랜치: `feature/flyway-initial-migration`
- 목적: JPA `ddl-auto` 기반 schema 생성과 런타임 기본 데이터 initializer를 Flyway migration으로 전환

---

## 1. 결정 요약

운영에 필요한 schema와 기본 선택지 데이터는 Flyway migration으로 관리한다.

- `V1__init_schema.sql`: 현재 엔티티 기준 전체 schema 생성
- `V2__seed_default_assets.sql`: 기본 캐릭터, 캐릭터 이미지, 캐릭터 썸네일, 롤링페이퍼 래퍼, 래퍼 이미지 seed

`data.sql`은 사용하지 않는다.

이유:

- 캐릭터와 래퍼는 로컬 샘플 데이터가 아니라 API가 의존하는 운영 기준 데이터다.
- migration으로 관리해야 dev/prod/test 기준 데이터 변경 이력이 PR에서 리뷰된다.
- `data.sql`은 JPA/Flyway 실행 순서와 profile별 동작을 추가로 제어해야 해서 schema 소유권이 흐려진다.

---

## 2. Runtime 경계

기존 런타임 initializer는 제거한다.

- `DefaultCharacterInitializer`
- `DefaultRollingPaperWrapperInitializer`

기본 데이터 생성/변경 책임은 Flyway가 가진다.
애플리케이션 부팅 중 seed data를 보정하지 않는다.

---

## 3. DB 초기화 절차

이번 전환에서는 dev/prod 데이터를 모두 버릴 수 있다는 전제다.
따라서 기존 DB에 baseline을 찍지 않고, DB를 비운 뒤 Flyway가 처음부터 migration을 적용한다.

권장 절차:

```bash
# dev/prod 각각 대상 DB 확인 후 실행
DROP DATABASE <database_name>;
CREATE DATABASE <database_name> CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

이후 애플리케이션을 배포/기동하면 Spring Boot가 Flyway migration을 자동 실행한다.

---

## 4. 검증 기준

- `spring.jpa.hibernate.ddl-auto=validate`
- 테스트 profile은 기존 테스트 격리를 위해 Flyway를 비활성화하고 H2 `create-drop`을 유지한다.
- 별도 `FlywayMigrationTest`에서 H2 MySQL mode로 `db/migration` SQL을 실행해 schema/seed 적용을 검증한다.
