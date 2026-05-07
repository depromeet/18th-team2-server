# Secret 관리 가이드

이 프로젝트는 [suker80/18th-team2-server-secret](https://github.com/suker80/18th-team2-server-secret) 레포지토리를 git submodule로 사용하여 비밀 설정 파일을 관리합니다.

## 구조

```
config/
└── secret/          # git submodule (depromeet/18th-team2-server-secret)
    └── application-secret.yml
```

`application.yml`에서 `spring.profiles.include: secret`으로 자동 로드됩니다.

## 최초 설정

### 방법 1: 클론 시 함께 가져오기

```bash
git clone --recurse-submodules https://github.com/depromeet/18th-team2-server.git
```

### 방법 2: 이미 클론한 경우

```bash
./scripts/init-secrets.sh
```

이 스크립트는 submodule 초기화 + secret 파일 심볼릭 링크 생성을 자동으로 수행합니다.

### 방법 3: 수동

```bash
git submodule update --init --recursive
```

Gradle 빌드 시 `config/secret/` 디렉터리의 yml 파일이 자동으로 리소스에 포함됩니다.

## Secret 파일 업데이트

secret 레포지토리에 변경사항이 있을 때:

```bash
cd config/secret
git pull origin main
cd ../..
git add config/secret
git commit -m "chore: update secret submodule"
```

## 빌드

Gradle `processResources` 태스크가 `config/secret/*.yml` 파일을 빌드 리소스에 자동 복사합니다. 별도 설정 없이 `./gradlew build`만 실행하면 됩니다.

## 주의사항

- `application-secret.yml`은 `.gitignore`에 등록되어 있어 메인 레포에 커밋되지 않습니다
- secret 레포 접근 권한이 필요합니다 (suker80/18th-team2-server-secret 접근 권한)
- CI/CD에서는 deploy key 또는 PAT를 사용하여 submodule을 체크아웃해야 합니다
