# 레이어드 아키텍처 규칙

## 패키지 구조

```
feature/
├── api/              Controller, Request/Response DTO
├── application/
│   ├── usecase/      흐름 제어, @Transactional 경계
│   └── service/      Aggregate 단위 행위
├── domain/
│   ├── entity/       행동 있는 JPA 엔티티
│   ├── policy/       여러 엔티티 걸친 도메인 규칙
│   └── vo/           enum, value object
└── infrastructure/
    └── persistence/  JpaRepository, 외부 어댑터
```

## 의존 방향

```
api → usecase → service → infrastructure
         │          │
         └── domain ◀┘
```

- Controller → UseCase만 허용
- UseCase → Service 조합, 다른 feature UseCase 허용
- Service → 자기 Aggregate Repository/Domain만 허용
- Service → Service 호출 금지 (어디든)
- Service → 다른 feature 접근 금지

## UseCase 규칙

- 1 클래스 = 1 public 메서드 (`invoke` / `execute`)
- `@Transactional`은 UseCase에만 선언
- 60줄 이내, 생성자 의존성 5개 이내
- Repository 쓰기(save/delete) 직접 호출 금지 → Service 위임
- Response DTO 변환은 UseCase 책임

## Service 규칙

- 1 Aggregate = 1 Service
- `@Transactional` 선언 금지
- 150줄 이내, 의존성 4개 이내, public 메서드 5개 이내
- 도메인 동사 메서드명 사용 (`join`, `activate`, `close`)
- 도메인 객체 반환 (Response DTO 변환 금지)

## Domain 규칙

- Entity는 행동을 가진다 (anemic 모델 금지)
- 여러 Entity 걸친 규칙은 Policy로 분리
- Repository 의존 금지
