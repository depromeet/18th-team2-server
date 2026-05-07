# Swagger API 인터페이스 패턴 가이드

## 개요

컨트롤러의 Swagger 문서 어노테이션을 `*Api.kt` 인터페이스로 분리하여, **문서와 비즈니스 로직을 완전히 분리**하는 패턴입니다.

## 구조

```
party/controller/
├── PartyApi.kt              ← Swagger 문서 (인터페이스)
├── PartyController.kt       ← 비즈니스 로직 (구현체)
├── PartyInviteApi.kt
└── PartyInviteController.kt

common/swagger/
├── AuthErrorResponses.kt         ← 401 (인증 실패)
├── ForbiddenResponse.kt          ← 403 (권한 없음)
├── ValidationErrorResponse.kt    ← 400 (입력값 검증 실패)
└── InternalServerErrorResponse.kt ← 500 (서버 내부 오류)
```

## 사용법

### 1. 새 API 엔드포인트 추가 시

**Api 인터페이스에 메서드 + Swagger 어노테이션 정의:**

```kotlin
// ExampleApi.kt
@Tag(name = "Example", description = "예시 API")
interface ExampleApi {

    @Operation(
        summary = "예시 조회",
        security = [SecurityRequirement(name = "Bearer Authentication")],
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "조회 성공",
    )
    @AuthErrorResponses          // 401 자동 포함
    @InternalServerErrorResponse // 500 자동 포함
    fun getExample(
        @Parameter(hidden = true) principal: UserPrincipal,
        @Parameter(description = "예시 ID", example = "1") id: Long,
    ): ApiResponse<ExampleResponse>
}
```

**Controller는 인터페이스 구현만:**

```kotlin
// ExampleController.kt
@RestController
@RequestMapping("/api/v1/examples")
class ExampleController(
    private val exampleService: ExampleService,
) : ExampleApi {

    @GetMapping("/{id}")
    override fun getExample(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ApiResponse<ExampleResponse> =
        ApiResponse.success(exampleService.getExample(id))
}
```

### 2. 공통 에러 어노테이션 사용

메서드 위에 붙이면 해당 에러 응답이 Swagger UI에 자동으로 포함됩니다.

| 어노테이션 | 응답 코드 | 언제 사용 |
|---|---|---|
| `@AuthErrorResponses` | 401 | 로그인 필수 API |
| `@ForbiddenResponse` | 403 | 리소스 소유자 검증 API |
| `@ValidationErrorResponse` | 400 | `@Valid` 사용하는 API |
| `@InternalServerErrorResponse` | 500 | 모든 API |

### 3. 인증 선택 API (토큰 있으면 쓰고, 없어도 동작)

```kotlin
@Operation(
    summary = "파티 정보 조회",
    security = [
        SecurityRequirement(name = "Bearer Authentication"),
    ],
)
@OptionalAuth
```

`@OptionalAuth`를 붙이면 OpenAPI security에 `Bearer Authentication`과 빈 security requirement가 함께 등록되어, 토큰이 없어도 호출 가능한 API로 표시된다.

### 4. 도메인 특화 에러 응답

공통 어노테이션에 없는 에러(404, 409 등)는 인터페이스에서 직접 작성합니다.

```kotlin
@SwaggerApiResponse(
    responseCode = "404",
    description = "존재하지 않는 파티",
    content = [Content(
        mediaType = "application/json",
        schema = Schema(implementation = ErrorResponse::class),
        examples = [ExampleObject(value = """
            {
              "status": 404,
              "error": {
                "code": "PARTY_NOT_FOUND",
                "message": "파티를 찾을 수 없습니다"
              }
            }
        """)]
    )]
)
```

## 규칙

- **성공 응답(2xx)**: `@SwaggerApiResponse`에 `content`를 지정하지 않는다. 리턴 타입(`ApiResponse<T>`)에서 스키마가 자동 추론된다. `content`를 넣으면 자동 추론이 꺼져서 Schemas 섹션에 응답 DTO가 등록되지 않는다.
- **에러 응답(4xx, 5xx)**: `content`에 `schema`와 `examples`를 명시한다. 에러는 예외 핸들러에서 발생하므로 자동 추론이 불가능하다.
- **Api 인터페이스**: `@Tag`, `@Operation`, `@SwaggerApiResponse`, `@Parameter`, 공통 에러 어노테이션만 작성
- **Controller**: `@RestController`, `@RequestMapping`, HTTP 메서드 매핑(`@GetMapping` 등), `@AuthenticationPrincipal`, `@PathVariable`, `@RequestBody`만 작성
- **ExampleObject**: 에러 응답에는 반드시 실제 JSON 예시를 포함 (schema만으로는 `"code": "string"` 같이 표시됨)
- **import alias**: Swagger의 `@ApiResponse`는 우리 `ApiResponse`와 이름이 겹치므로 `import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse`로 사용

## 새 도메인 추가 체크리스트

1. `{도메인}/controller/{Domain}Api.kt` 인터페이스 생성
2. `@Tag(name = "도메인명")` 지정
3. 각 메서드에 `@Operation` + 성공 응답 + 에러 응답 작성
4. 공통 에러는 어노테이션으로, 도메인 특화 에러는 직접 작성
5. Controller에서 인터페이스 구현 (`: {Domain}Api`)
6. Controller에서 기존 Swagger 어노테이션 제거
