---
name: team-flow
description: 이슈 생성 → 브랜치 생성 → checkout 전체 플로우. 팀 이슈 템플릿과 브랜치 컨벤션을 자동 적용.
---

# /team-flow

이슈 생성부터 브랜치 checkout까지 한번에 처리합니다.

## 사용법

```
/team-flow OAuth 로그인 구현
/team-flow 로그인 실패 시 에러 메시지 미노출 버그
```

## 실행 순서

### Step 1: 작업 타입 판별

사용자의 설명을 분석하여 타입을 결정한다:

| 타입 | 판별 기준 | 이슈 prefix | 브랜치 prefix | 라벨 |
|------|----------|------------|--------------|------|
| feat | 새 기능, 구현, 추가 | `[Feat]` | `feature/` | `✨ FEATURE` |
| fix | 버그, 오류, 수정, 실패 | `[Fix]` | `fix/` | (없으면 생략) |
| chore | 설정, CI, 의존성, 빌드 | `[Chore]` | `chore/` | (없으면 생략) |
| refactor | 리팩토링, 구조 개선 | `[Refactor]` | `refactor/` | (없으면 생략) |
| docs | 문서, README | `[Docs]` | `docs/` | (없으면 생략) |
| test | 테스트 추가/수정 | `[Test]` | `test/` | (없으면 생략) |

판별이 애매하면 사용자에게 질문한다.

### Step 2: 현재 상태 확인

```bash
git status
git branch --show-current
```

- 커밋되지 않은 변경사항이 있으면 경고하고 계속 진행할지 확인

### Step 3: GitHub 이슈 생성

이슈 템플릿에 맞게 이슈를 생성한다:

```bash
gh issue create \
  --title "[<타입>] <한국어 설명>" \
  --label "<라벨>" \
  --body "$(cat <<'EOF'
## 📝 Description
<사용자 설명을 기반으로 한 줄 요약>

## 📌 TODO
- [ ] <구현해야 할 항목 1>
- [ ] <구현해야 할 항목 2>
- [ ] <구현해야 할 항목 3>

## 📎 References
EOF
)"
```

- 이슈 번호를 캡처한다 (예: `#13`)
- 라벨이 존재하지 않으면 `--label` 옵션 생략

### Step 4: 브랜치 생성 및 checkout

```bash
git checkout develop
git pull origin develop
git checkout -b <prefix>/<kebab-case-설명>
```

**브랜치명 규칙:**
- 영문 kebab-case 사용
- 한국어 설명을 영문으로 변환 (예: "OAuth 로그인 구현" → `feature/oauth-login`)
- 30자 이내 권장

### Step 5: 결과 출력

```
✅ 이슈 생성: #13 [Feat] OAuth 로그인 구현
✅ 브랜치 생성: feature/oauth-login
✅ 현재 브랜치: feature/oauth-login

다음 단계:
  1. 기능 구현
  2. /team-commit 으로 커밋
  3. /team-pr #13 으로 PR 생성
```

## 금지 사항

- develop 브랜치에서 직접 작업 시작 금지 (반드시 새 브랜치 생성)
- 브랜치명에 한국어 사용 금지
- 이슈 없이 브랜치 생성 금지 (이 스킬은 항상 이슈를 먼저 만듦)
