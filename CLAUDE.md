# 18th-team2-server

## 기술 스택

- Kotlin + Spring Boot
- Gradle (Kotlin DSL)
- JPA / Hibernate

## 규칙

- [Git 규칙](.claude/rules/git.md) — 커밋/브랜치/PR 컨벤션, 금지 사항

## 테스트

- 테스트 작성·실행 규칙은 [docs/testing-rules.md](docs/testing-rules.md) 참고
- `@SpringBootTest` / `@DataJpaTest`는 반드시 `TestcontainersConfiguration` 경유 (`@Import` 또는 base class 상속)
- 단일 테스트: `./gradlew test --tests "<FQCN>"`
- 컨테이너 누수 검증: `docker ps -a --filter "label=org.testcontainers"` → 0개

## 아키텍처

- [레이어드 아키텍처 규칙](.claude/rules/layered-architecture.md) 필수 준수

## 팀 스킬

- `/team-flow` — 이슈 생성 → 브랜치 생성 → checkout 전체 플로우
- `/team-commit` — 컨벤션에 맞는 커밋 생성 (빌드+테스트 검증 포함)
- `/team-pr` — 기능 단위 PR 생성 (PR 템플릿 자동 적용)
