# Flyway Migration Design

- 작성일: 2026-05-06
- 기준 브랜치: `feature/flyway-initial-migration`
- 목적: JPA `ddl-auto` 기반 schema 생성과 런타임 기본 데이터 initializer를 Flyway migration으로 전환

---

## 1. 결정 요약

운영에 필요한 schema와 기본 선택지 데이터는 Flyway migration으로 관리한다.

- Spring Boot 4에서는 Flyway 자동 실행을 위해 `spring-boot-starter-flyway`를 사용한다.
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

이번 전환은 **초기 마이그레이션 전환 작업**에만 적용한다.
기존 DB에 baseline을 찍지 않고, DB를 비운 뒤 Flyway가 처음부터 migration을 적용한다.

주의:

- dev/local은 데이터 폐기가 허용된 환경에서만 drop/recreate를 실행한다.
- prod는 테스트 클러스터나 명시적으로 폐기 가능한 운영 데이터에 한해서만 drop/recreate를 허용한다.
- prod 실행 전에는 반드시 DB 스냅샷 또는 dump를 생성하고, 복구 명령/담당자/검증 절차를 확정한다.
- 실제 사용자 데이터가 보존되어야 하는 prod에서는 이 절차를 사용하지 않고 별도 증분 migration/backfill 계획을 작성한다.

권장 절차:

```bash
# dev/prod 각각 대상 DB 확인 후 실행
DROP DATABASE <database_name>;
CREATE DATABASE <database_name> CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

이후 애플리케이션을 배포/기동하면 Spring Boot가 Flyway migration을 자동 실행한다.

---

## 4. 로컬 적용 절차

로컬도 기존 `ddl-auto=update`가 만든 테이블이 남아 있으면 `V1__init_schema.sql`의 `CREATE TABLE`이 실패한다.
따라서 이 브랜치 적용 후에는 로컬 DB도 한 번 drop/recreate한 뒤 앱을 실행한다.

로컬 Docker DB 기준:

```bash
docker exec team2-local-db mysql -uroot -proot -e "DROP DATABASE IF EXISTS \`team2-local-db\`; CREATE DATABASE \`team2-local-db\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

앱 실행:

```bash
./gradlew bootRun
```

Flyway 적용 확인:

```bash
docker exec team2-local-db mysql -uroot -proot team2-local-db -e "SHOW TABLES;"
docker exec team2-local-db mysql -uroot -proot team2-local-db -e "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

기본 seed 데이터 확인:

```bash
docker exec team2-local-db mysql -uroot -proot team2-local-db -e "SELECT COUNT(*) AS avatars FROM avatar; SELECT COUNT(*) AS wrappers FROM rolling_paper_wrapper; SELECT COUNT(*) AS images FROM image;"
```

기대값:

```text
avatar = 5
rolling_paper_wrapper = 3
image = 13
flyway_schema_history = V1, V2 success
```

---

## 5. 검증 기준

- `spring.jpa.hibernate.ddl-auto=validate`
- 테스트 profile은 기존 테스트 격리를 위해 Flyway를 비활성화하고 H2 `create-drop`을 유지한다.
- 별도 `FlywayMigrationTest`에서 H2 MySQL mode로 `db/migration` SQL을 실행해 schema/seed 적용을 검증한다.

---

## 6. Migration 추가 규칙

- migration 파일은 `src/main/resources/db/migration/`에 추가한다.
- 파일명은 `V{version}__{description}.sql` 형식을 따른다.
  - 예: `V3__add_party_status_column.sql`
  - 예: `V4__update_wrapper_image_urls.sql`
- `V`는 대문자로 쓰고, version과 description 사이에는 underscore 두 개(`__`)를 사용한다.
- 이미 배포되어 `flyway_schema_history`에 기록된 migration 파일은 수정하지 않는다.
- schema 변경, 기준 seed 변경, 정적 이미지 URL 변경은 항상 다음 버전 migration으로 추가한다.
- migration 적용 이력은 `flyway_schema_history`에서 확인한다.
