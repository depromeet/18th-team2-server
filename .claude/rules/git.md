# Git 규칙

## 커밋 컨벤션

- 포맷: `<type>: <한국어 설명>`
- type: feat, fix, chore, docs, test, refactor, perf, ci
- scope 사용하지 않음
- 한국어 명사형 종결 ("~추가", "~수정", "~구현")
- 50자 이내, 마침표 없음

## 브랜치 컨벤션

- `feature/<영문-kebab-case>` — 새 기능
- `fix/<영문-kebab-case>` — 버그 수정
- `chore/<영문-kebab-case>` — 설정, 유지보수
- `refactor/<영문-kebab-case>` — 리팩토링
- `docs/<영문-kebab-case>` — 문서
- `test/<영문-kebab-case>` — 테스트

## PR 규칙

- 기능 단위로 PR 생성 (하나의 PR = 하나의 기능)
- base 브랜치: `develop`
- PR 템플릿(`.github/PULL_REQUEST_TEMPLATE.md`) 필수 준수
- 빌드 + 테스트 통과 후 PR 생성

## 금지 사항

- `--no-verify` 사용 금지
- `main` 브랜치 직접 커밋/푸시 금지 (마스터 브랜치, 보호 대상)
- `develop` 브랜치 직접 커밋 금지
- `git add -A` / `git add .` 사용 금지 (파일 개별 지정)
- 시크릿 파일 커밋 금지 (`.env`, `application-*.yml`, `*.pem`, `*.key` 등)
- 영문 커밋 메시지 금지
