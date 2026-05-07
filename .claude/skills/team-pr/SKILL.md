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

### Step 2: 빌드 및 테스트 검증

```bash
./gradlew build
```

- 컴파일 + 전체 테스트 수행
- **실패 시** → "빌드/테스트가 실패했습니다. 에러를 수정한 후 다시 시도하세요." 후 **중단**

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
- 빌드/테스트 실패 상태에서 PR 생성 금지
- PR 본문에서 팀 템플릿 섹션(Issue, Context, Changes, Review Focus, Check List) 누락 금지
- `--no-verify` 플래그 사용 금지
- 여러 기능을 하나의 PR에 묶는 것 지양 (경고)
