---
name: team-commit
description: 팀 컨벤션에 맞는 한국어 커밋 생성. 전체 빌드+테스트 검증, 시크릿 검출, 파일별 스테이징 강제.
---

# /team-commit

팀 컨벤션에 맞게 커밋을 생성합니다. 한국어 커밋 메시지, 전체 빌드+테스트 검증 포함.

## 사용법

```
/team-commit                        # 변경사항 분석 후 자동 커밋 메시지 생성
/team-commit 로그인 API 엔드포인트 추가  # 설명 기반으로 커밋 메시지 생성
```

## 실행 순서 (반드시 이 순서를 따를 것)

### Step 1: 변경사항 확인

```bash
git status
git diff --cached
git diff
```

- 변경사항이 없으면 → "커밋할 변경사항이 없습니다" 출력 후 **중단**

### Step 2: 가드레일 체크

#### 2-1. 시크릿 파일 검출

변경/staged 파일 목록에서 아래 패턴이 있으면 **해당 파일을 스테이징에서 제외**하고 경고:

| 패턴 | 설명 |
|------|------|
| `.env`, `.env.*` | 환경 변수 파일 |
| `application-*.yml`, `application-*.yaml`, `application-*.properties` | Spring 프로파일 설정 (`application.yml`은 허용) |
| `*secret*`, `*credential*` | 시크릿 관련 (git submodule 디렉토리 제외) |
| `*.pem`, `*.key`, `*.p12`, `*.jks` | 인증서/키 파일 |

#### 2-2. 시크릿 내용 검출

`git diff`와 `git diff --cached` 내용에서 아래 패턴이 있으면 **경고** (사용자 확인 후 진행):

- `password=`, `secret=`, `api[_-]key=`, `token=` 뒤에 실제 값이 있는 경우
- `AKIA` (AWS Access Key 패턴)
- `ghp_`, `gho_`, `github_pat_` (GitHub 토큰 패턴)

#### 2-3. 브랜치 확인

- `main` 브랜치에서 커밋하려 하면 → **경고**: "main 브랜치에 직접 커밋하고 있습니다. 계속하시겠습니까?"
- 브랜치명이 `feature/`, `fix/`, `chore/`, `refactor/`, `docs/`, `test/` 중 하나로 시작하지 않으면 → **경고**

### Step 3: 빌드 및 테스트 검증

```bash
./gradlew build
```

이 명령은 컴파일 + 전체 테스트를 수행한다.

- **실패 시** → 에러 메시지를 보여주고 **중단**
- "빌드/테스트가 실패했습니다. 에러를 수정한 후 다시 시도하세요." 출력
- 에러 로그에서 핵심 실패 원인을 요약하여 제시

### Step 4: 커밋 메시지 생성

1. `git log --oneline -5`로 최근 커밋 스타일 참조
2. 변경 내용 분석 후 타입 결정:

| 타입 | 기준 |
|------|------|
| `feat` | 새로운 기능, 엔드포인트, 엔티티, DTO 추가 |
| `fix` | 버그 수정, 예외 처리 개선, 오류 해결 |
| `refactor` | 동작 변경 없는 코드 구조 개선 |
| `chore` | 설정, 의존성, CI/CD, 빌드, .gitignore 등 |
| `docs` | 문서, 주석, README 변경 |
| `test` | 테스트 코드 추가/수정 |
| `perf` | 성능 개선, 쿼리 최적화 |
| `ci` | GitHub Actions, CI 파이프라인 변경 |

3. **커밋 메시지 포맷:**

```
<type>: <한국어 설명>
```

- **한국어 필수** (영문 금지)
- scope 사용하지 않음
- 명사형 종결: "~추가", "~수정", "~설정", "~개선", "~구현"
- 50자 이내
- 마침표 없음

**좋은 예:**
```
feat: OAuth2 소셜 로그인 구현
fix: 로그인 시 NPE 발생하는 버그 수정
chore: Spring Boot 3.2로 버전 업그레이드
refactor: UserService 인증 로직 분리
test: UserController 단위 테스트 추가
```

**나쁜 예:**
```
feat: add OAuth2 login        ← 영문 금지
feat(auth): 로그인 구현        ← scope 사용 금지
feat: 로그인 구현.             ← 마침표 금지
update                         ← 타입 없음, 설명 불충분
```

### Step 5: 파일 스테이징 및 커밋

1. **`git add -A` 또는 `git add .` 사용 금지** → 관련 파일만 개별 지정
2. 기능 단위로 관련 파일만 스테이징:
   - 하나의 기능에 관련된 파일들을 묶어서 커밋
   - 관련 없는 파일 변경이 섞이지 않도록 주의

3. 사용자에게 아래를 보여주고 확인받은 후 커밋:
   - 스테이징할 파일 목록
   - 커밋 메시지

4. 커밋 실행:
```bash
git add <file1> <file2> ...
git commit -m "<type>: <한국어 설명>"
```

5. 결과 표시:
```bash
git log --oneline -1
```

## 금지 사항

- `--no-verify` 플래그 절대 사용 금지
- `--amend` 사용 금지 (항상 새 커밋 생성)
- `git add -A`, `git add .` 사용 금지
- 시크릿 파일 스테이징 금지
- 영문 커밋 메시지 금지
- scope 사용 금지 (예: `feat(auth):` ← 이렇게 하지 않음)
