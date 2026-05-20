# 마이페이지 계정 관리 API

- **작성일**: 2026-05-14
- **작성자**: taegyu.choi
- **상태**: Draft (사용자 리뷰 대기)
- **워크트리**: `.worktrees/feature-my-page-account` (브랜치: `design/my-page-account`)

## 1. 배경

마이페이지 화면(계정 관리)을 서버 측에서 지원하기 위한 API를 신설한다.

해당 화면은 다음 요소를 노출한다:

- 사용자 닉네임
- 카카오 연결 정보 (provider + 연결 일자)
- 1:1 문의 (카카오 오픈 채팅 링크)
- 로그아웃 버튼 + 확인 팝업

로그아웃은 **프론트엔드 단독 처리**로 결정 — 서버 API 신설 없음. JWT는 stateless이므로 클라이언트가 로컬 토큰을 폐기하면 사실상 로그아웃이다.

## 2. 범위

이번 PR에서 구현할 것:

- `GET /api/me/account` — 마이페이지 계정 관리 화면용 단일 조회 API

이번 PR에서 구현하지 않을 것:

- 로그아웃 API (프론트 처리)
- 닉네임 변경, 회원 탈퇴, 1:1 문의 내역 등 (이미지 화면에 없음)
- 토큰 무효화 (블랙리스트, Refresh Token 등) — 별도 보안 spec
- `1:1 문의 URL`을 DB로 관리하는 admin 기능 (정적 yml로 충분)

## 3. 가정과 결정 (Decisions)

| ID | 결정 | 근거 |
|----|------|------|
| D1 | "카카오 연결일" = `User.createdAt` (회원가입일) | 현재 단일 provider 가입 모델. 별도 컬럼 불필요 |
| D2 | 1:1 문의 카카오 오픈 채팅 URL은 `application.yml` 정적 설정 | 운영자 동적 변경 요구 없음, DB 테이블 불필요 (YAGNI) |
| D3 | 로그아웃은 클라이언트 전담 (서버 API 없음) | 사용자 결정. JWT stateless 환경 + 현재 보안 요구사항 없음 |
| D4 | 기존 `GET /api/auth/me` 유지, 신규 `GET /api/me/account`로 분리 | `/api/auth/me`는 인증 사용자 단순 조회. 마이페이지는 화면 전용 응답 → 책임 분리 |
| D5 | 신규 코드는 **layered architecture**로 작성 | 신규 feature는 layered 의무화. party 도메인 패턴 따름 |
| D6 | UseCase가 Service 없이 `UserRepository` read-only 호출 | 레이어드 규칙은 Repository **쓰기**(save/delete) 직접 호출만 금지. 단순 조회는 UseCase → Repository 허용 |
| D7 | `connectedAt`은 `LocalDate` ISO 형식 반환, 시각 포맷팅은 클라이언트 책임 | API는 데이터, 표현은 클라이언트 |
| D8 | DB 스키마 변경 없음 | 모든 필드를 기존 컬럼 + yml 설정에서 도출 가능 |

## 4. API 명세

### 4.1 GET /api/me/account

**인증**: 필요 (JWT, Bearer)

**Request**: 없음 (Authorization 헤더의 JWT로 사용자 식별)

**Response 200**:

```json
{
  "status": 200,
  "data": {
    "nickname": "김이라",
    "provider": "KAKAO",
    "connectedAt": "2026-02-23",
    "supportChatUrl": "https://open.kakao.com/o/xxxxx"
  }
}
```

응답 envelope은 프로젝트 공통 `ApiResponse<T> = { status: Int, data: T? }`을 따른다.

**필드 정의**:

| 필드 | 타입 | 설명 | 출처 |
|---|---|---|---|
| `nickname` | String | 사용자 닉네임 | `User.name` |
| `provider` | String (enum) | 연결된 OAuth provider | `User.provider` (KAKAO / GOOGLE / APPLE / NAVER) |
| `connectedAt` | String (ISO date, `YYYY-MM-DD`) | 연결 일자 (= 가입일) | `User.createdAt.toLocalDate()` |
| `supportChatUrl` | String (URL) | 1:1 문의 카카오 오픈 채팅 링크 | `application.yml` 설정값 |

**에러 응답**:

| 상태 | 코드 | 시나리오 |
|---|---|---|
| 401 | (JWT Entry Point) | Authorization 헤더 없음 또는 JWT invalid |
| 401 | `AUTH_USER_NOT_FOUND` | 토큰의 userId가 DB에 없음 (계정 삭제 등). 기존 `ErrorCode.AUTH_USER_NOT_FOUND` 재사용 (의미상 인증 실패) |

## 5. 패키지 구조 (Layered)

```text
src/main/kotlin/com/team2/server/me/
├── api/
│   └── MeAccountController.kt
└── application/
    ├── dto/
    │   └── MeAccountResult.kt
    └── usecase/
        └── GetMeAccountUseCase.kt
```

> `MeAccountResult` 와 외부 응답 형태가 1:1 매핑(필드 동일, 직렬화 가공 없음)이므로
> 별도 `api/dto/MeAccountResponse` 를 두지 않고 Controller 가 `MeAccountResult` 를
> 그대로 `ApiResponse` 로 감싼다. (레이어드 룰: "1:1 매핑이면 application/dto 하나만 두고
> Controller 는 ApiResponse wrapper 만 씌운다")

추가로 설정 클래스 1개:

```text
src/main/kotlin/com/team2/server/me/config/
└── SupportProperties.kt          # support.chat-url 바인딩
```

### 5.1 레이어 책임

**MeAccountController** (`me/api/`)
- `@AuthenticationPrincipal UserPrincipal` 받음
- `GetMeAccountUseCase.invoke(userId)` 호출 → `MeAccountResult` 수신
- `ApiResponse.success(result)` 로 그대로 wrap (1:1 매핑이라 별도 Response DTO 없음)

**GetMeAccountUseCase** (`me/application/usecase/`)
- `@Transactional(readOnly = true)`
- 생성자 의존성: `UserRepository`, `SupportProperties`
- 흐름:
  1. `userRepository.findById(userId)` → 없으면 `BusinessException(AUTH_USER_NOT_FOUND)`
  2. `User`와 `supportProperties.chatUrl`을 합쳐 `MeAccountResult` 빌드
  3. 반환 (API DTO 의존 금지 — application 레이어 DTO 만 노출)
- 60줄 이내, public 메서드 1개 (`invoke`)

**MeAccountResult** (`me/application/dto/`)
- application 레이어 DTO. UseCase 반환 타입
- `companion object`에 `from(user: User, supportChatUrl: String)` 정적 팩토리

**SupportProperties** (`me/config/`)
- `@ConfigurationProperties(prefix = "support")`
- 필드: `chatUrl: String`
- `@EnableConfigurationProperties(SupportProperties::class)` 적용 위치는 동일 모듈 내 `@Configuration` 클래스 또는 `SpringBootApplication`

### 5.2 레이어드 규칙 점검

- ✅ Controller → UseCase만 호출
- ✅ UseCase는 1 public 메서드 (`invoke`)
- ✅ `@Transactional`은 UseCase에만
- ✅ UseCase가 `UserRepository`를 직접 read-only로 호출 (쓰기 없음 → 규칙 위반 아님)
- ✅ Service 없음 (read-only 단순 조회, 도메인 행위 없음)
- ✅ UseCase 는 application 레이어 DTO(`MeAccountResult`)만 반환 — API DTO 의존 없음
- ✅ Controller 는 `MeAccountResult` 를 `ApiResponse` 로만 wrap (1:1 매핑이라 별도 Response DTO 미사용)

## 6. 설정 (application.yml)

루트 application.yml 또는 환경별 yml에 추가:

```yaml
support:
  chat-url: "https://open.kakao.com/o/xxxxx"
```

- `application-local.yml`, `application-prod.yml`에서 환경별 분기 가능
- 공개 URL이므로 시크릿 아님 → 일반 yml에 보관 OK

## 7. 보안

- 엔드포인트는 인증 필수 → `SecurityConfig`의 `anyRequest().authenticated()`에 포함 (기본값, 추가 설정 불필요)
- 응답에 이메일/생년월일 등 불필요한 PII 포함 안 함 (닉네임만)
- `supportChatUrl`이 yml 설정값 그대로 응답되지만 운영자가 직접 관리하는 값이므로 검증 불필요

## 8. 테스트 전략

| 레이어 | 테스트 | 도구 | 케이스 |
|---|---|---|---|
| Controller | `MeAccountControllerTest` | `@SpringBootTest + @AutoConfigureMockMvc + @Import(TestcontainersConfiguration::class)` | (1) 인증 + 정상 200, (2) 미인증 401, (3) userId 없음 401 |
| UseCase | `GetMeAccountUseCaseTest` | 순수 단위 테스트 (fake `UserRepository`) | (1) 정상 매핑, (2) User 없음 → `BusinessException(AUTH_USER_NOT_FOUND)` |

- Controller 통합 테스트는 프로젝트 표준 (`MePartyControllerTest` 등과 동일 패턴) — `@WebMvcTest` 미사용
- UseCase 테스트는 fake repository로 빠른 단위 테스트

## 9. 마이그레이션 / 호환성

- 기존 `/api/auth/me` 유지 (deprecate 안 함). 클라이언트가 점진적으로 신규 API로 이전 가능
- DB 스키마 변경 없음 → Flyway 마이그레이션 불필요
- 신규 yml 키 `support.chat-url`이 누락된 환경에서는 애플리케이션 부팅 실패 → 모든 환경 yml에 추가 필요

## 10. 미해결 / 확장 여지

- **닉네임 변경 API**: 별도 spec에서 다룸
- **회원 탈퇴 API**: 별도 spec
- **다중 provider 연결 (카카오 + 구글)**: 현재 단일 provider 모델. 확장 시 `connectedAt`이 provider별로 필요 → 별도 테이블 `user_provider_connection`으로 분리하는 마이그레이션 필요
- **1:1 문의 URL을 사용자 segment별로 다르게 보여주기**: 요구 없음. 필요 시 yml → DB 이전 가능
- **로그아웃 서버 API**: 추후 보안 요구 발생 시 `POST /api/auth/logout` + Redis 블랙리스트 또는 `tokenVersion` 패턴 도입 가능

## 11. 작업 순서 (구현 계획용 힌트)

1. `SupportProperties` + `application.yml` 키 추가
2. `MeAccountResult` (application/dto) 정의 — `from(user, supportChatUrl)` 팩토리
3. `GetMeAccountUseCase` 구현 + 단위 테스트
4. `MeAccountController` 구현 (`ApiResponse<MeAccountResult>` 직접 반환) + MockMvc 테스트
5. 빌드 + 전체 테스트 통과 확인
6. PR 생성 (base: `develop`)

상세 구현 계획은 `writing-plans` 스킬로 별도 plan 문서 작성 예정.
