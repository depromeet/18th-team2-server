---
name: team-pr
description: 팀 PR 템플릿에 맞는 기능 단위 Pull Request 생성. 빌드+테스트 검증, 이슈 연결, 라벨 자동 설정.
---

# /team-pr

팀 PR 템플릿에 맞게 기능 단위 Pull Request를 생성합니다.

## 사용법

```
/team-pr              # 자동으로 PR 내용 생성
/team-pr #13          # 이슈 #13과 연결하여 PR 생성
/team-pr --draft      # Draft PR로 생성
```

## 실행 순서 (반드시 이 순서를 따를 것)

### Step 1: 사전 검증

```bash
git branch --show-current
git status
git log develop..HEAD --oneline
git diff develop...HEAD --stat
```

**차단 조건:**

| 조건 | 메시지 | 동작 |
|------|--------|------|
| 현재 브랜치가 `develop` | "develop 브랜치에서는 PR을 생성할 수 없습니다" | **중단** |
| `develop..HEAD` 커밋 없음 | "develop 대비 변경사항이 없습니다" | **중단** |
| 커밋되지 않은 변경사항 존재 | "커밋되지 않은 변경사항이 있습니다. `/team-commit`을 먼저 실행하세요" | **중단** |

### Step 2: 가드레일 — ktlint + 테스트 100%

**이 스킬은 아래 두 게이트를 모두 통과해야만 PR 생성으로 진행한다. 하나라도 실패하면 즉시 중단.**

#### 2-1. ktlint 검증 (필수, 100% 통과)

```bash
./gradlew ktlintCheck
```

- ktlint 위반이 **단 한 건이라도** 있으면 **중단**
- 안내 메시지:
  ```
  ❌ ktlint 검증 실패: <위반 개수>건
  → ./gradlew ktlintFormat 실행 후 변경분을 커밋하고 다시 시도하세요
  ```
- `ktlintFormat`을 자동 실행하지 않는다 (커밋되지 않은 변경 발생 방지)
- `ktlintCheck` 태스크가 없는 프로젝트면 **중단**하고 사용자에게 알림 (스킵 금지)

#### 2-2. 빌드 + 테스트 검증 (100% 성공 필수)

```bash
./gradlew build
```

- 컴파일 + 전체 테스트 수행
- **테스트 결과가 100% 성공이 아니면 중단** (실패/스킵/무시 0건이어야 함)
- 빌드 실패 시 → "빌드/테스트가 실패했습니다. 에러를 수정한 후 다시 시도하세요." 출력 후 **중단**
- 에러 로그에서 핵심 실패 원인 요약 + 테스트 리포트 경로(`build/reports/tests/test/index.html`) 안내
- `@Disabled`, `@Ignore`로 스킵된 신규 테스트가 있으면 **경고** (의도적인 경우만 사용자 확인 후 진행)

#### 2-3. 가드레일 우회 금지

- ktlint·테스트 실패를 무시하고 PR 생성 금지
- `-x test`, `-x ktlintCheck` 같은 태스크 제외 옵션 사용 금지
- `--no-verify`, `--skip-tests` 등 우회 플래그 사용 금지

### Step 3: 기능 단위 검증

`git diff develop...HEAD`를 분석하여 PR이 **하나의 기능 단위**인지 확인:

- 여러 관심사가 섞여 있으면 **경고**: "이 PR에 여러 기능이 섞여 있을 수 있습니다. 기능별로 분리하는 것을 권장합니다."
  - 예: Controller + 완전히 무관한 설정 변경, 서로 다른 도메인의 엔티티 변경
- 하나의 기능에 연관된 여러 레이어 변경(Controller + Service + Repository + Entity)은 **정상**

### Step 4: PR 내용 생성

#### 이슈 번호 결정

우선순위:
1. 사용자 인자: `/team-pr #13` → `#13`
2. 브랜치명에서 추출: `feature/13-oauth-login` → `#13`
3. 없으면 → `closes #` 비워두고 사용자에게 "이슈 번호를 입력해주세요" 알림

#### PR 타이틀

| 브랜치 prefix | PR 타이틀 prefix | 라벨 |
|--------------|-----------------|------|
| `feature/` | `[Feat]` | `✨ FEATURE` |
| `fix/` | `[Fix]` | - |
| `chore/` | `[Chore]` | - |
| `refactor/` | `[Refactor]` | - |
| `docs/` | `[Docs]` | - |
| `test/` | `[Test]` | - |

타이틀 형식: `[<타입>] <한국어 설명>`
- 커밋 히스토리와 diff를 기반으로 한국어 설명 생성
- 예: `[Feat] OAuth2 소셜 로그인 구현`

#### PR 본문

**반드시 아래 팀 PR 템플릿을 정확히 따른다:**

```markdown
## 🔗 Issue
- closes #<이슈번호>

## 💬 Context
<커밋 히스토리와 변경 내용을 기반으로 작업 배경, 맥락 설명>
<왜 이 변경이 필요했는지, 어떤 문제를 해결하는지>

## 🛠 Changes
<git diff develop...HEAD 기반 핵심 변경사항>
- <변경사항 1>
- <변경사항 2>
- <변경사항 3>

## 👀 Review Focus
<리뷰어가 집중해서 봐줬으면 하는 부분>
- <복잡한 비즈니스 로직>
- <새로운 패턴 도입>
- <성능 관련 결정>

## ⚠️ Side Effects
<이 PR이 머지/배포되었을 때 발생하는 부수 효과>
- **DB 마이그레이션**: <새 migration 파일, 스키마 변경, 롤백 가능 여부>
- **환경변수/설정**: <`application*.yml`, `.env` 추가·변경 항목 — 배포 전 반영 필요>
- **의존성 변경**: <`build.gradle.kts`에 추가/제거/버전업된 라이브러리>
- **API 변경(Breaking)**: <엔드포인트 시그니처, 응답 포맷, DTO 필드 변경>
- **외부 시스템 영향**: <연관된 프론트/타 서비스 영향, 알림 필요 대상>
- **운영 작업**: <수동 데이터 이관, 캐시 invalidate, 배포 순서 등>

> 해당 없는 항목은 `- 없음`으로 명시 (누락 방지)

## ✅ Check List
- [x] Assignees 등록
- [x] Label 등록
- [x] CI 통과 확인

---
> **Comment prefix** — P1: 필수 반영 / P2: 적극 고려 / P3: 사소한 의견
```

**작성 규칙:**
- Context: 단순 "~했습니다"가 아니라 **왜** 이 작업이 필요했는지 배경 설명
- Changes: 파일 목록이 아닌 **기능적 변경사항** 위주로 작성
- Review Focus: 코드 변경 복잡도를 분석하여 리뷰어가 집중할 포인트 제시
- Side Effects: 아래 자동 감지 규칙으로 채우고, 해당 없으면 `- 없음`으로 명시

**Side Effects 자동 감지 규칙 (`git diff develop...HEAD` 분석):**

| 감지 대상 | 패턴 | 표기 |
|----------|------|------|
| DB 마이그레이션 | `src/main/resources/db/migration/V*.sql`, Flyway/Liquibase 파일 추가 | 파일명 + 주요 DDL 요약 + 롤백 가능 여부 |
| 환경변수 | `application*.yml`, `.env*`에 신규 키 추가 | 키 이름 + 용도 + 기본값 유무 |
| 의존성 | `build.gradle.kts`의 `dependencies {}` 블록 변경 | 추가/제거/버전업 항목 |
| Breaking API | Controller의 `@RequestMapping`/`@*Mapping` 경로·메서드 변경, DTO 필드 제거·타입 변경 | 변경 전→후 시그니처 |
| 외부 시스템 | 신규/변경된 API 엔드포인트, 응답 스키마 | 프론트/타 서비스 알림 필요 표기 |
| 운영 작업 | 마이그레이션 + 데이터 백필, 캐시 키 변경, 배포 순서 의존성 | 수동 작업 절차 명시 |

감지된 항목이 하나라도 있으면 Side Effects 섹션은 **필수 작성**. 모두 해당 없을 때만 `- 없음`.

### Step 5: PR 생성

1. 리모트에 push (필요시):
```bash
git push -u origin <current-branch>
```

2. PR 생성:
```bash
gh pr create \
  --base develop \
  --title "<타이틀>" \
  --body "<본문>" \
  --assignee "@me"
```

- 라벨이 존재하면 `--label "<라벨>"` 추가
- `--draft` 인자가 있으면 `--draft` 플래그 추가

3. 결과 출력:
```
✅ PR 생성 완료
   URL: https://github.com/...
   이슈: closes #13
   라벨: ✨ FEATURE
```

## 금지 사항

- `develop` 브랜치에서 PR 생성 금지
- ktlintCheck 실패 상태에서 PR 생성 금지
- 빌드/테스트 실패 또는 스킵된 테스트가 있는 상태에서 PR 생성 금지
- `-x test`, `-x ktlintCheck` 등 가드레일 우회 옵션 사용 금지
- PR 본문에서 팀 템플릿 섹션(Issue, Context, Changes, Review Focus, Side Effects, Check List) 누락 금지
- Side Effects 자동 감지 항목이 발견되었는데 섹션을 비우거나 `- 없음`으로 채우는 것 금지
- `--no-verify` 플래그 사용 금지
- 여러 기능을 하나의 PR에 묶는 것 지양 (경고)
