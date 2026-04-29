#!/usr/bin/env bash
set -euo pipefail

SECRET_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/config/secret"

# 필수 키 목록 (yq 경로 형식 + 표시용 이름)
REQUIRED_KEYS=(
    ".spring.datasource.url|spring.datasource.url"
    ".spring.datasource.username|spring.datasource.username"
    ".spring.datasource.password|spring.datasource.password"
    '.spring.security.oauth2.client.registration.kakao["client-id"]|spring.security.oauth2.client.registration.kakao.client-id'
    '.spring.security.oauth2.client.registration.kakao["client-secret"]|spring.security.oauth2.client.registration.kakao.client-secret'
    ".app.jwt.secret|app.jwt.secret"
    '.app.jwt["expiration-hours"]|app.jwt.expiration-hours'
    '.app.oauth2["authorized-redirect-uris"]|app.oauth2.authorized-redirect-uris'
)

ENVS=("dev" "prod")
HAS_ERROR=false
MISSING_SUMMARY=""

for env in "${ENVS[@]}"; do
    FILE="$SECRET_DIR/application-secret-${env}.yml"

    if [ ! -f "$FILE" ]; then
        echo "FAIL: application-secret-${env}.yml 파일이 존재하지 않습니다."
        HAS_ERROR=true
        continue
    fi

    echo "Checking application-secret-${env}.yml ..."
    ENV_MISSING=""

    for entry in "${REQUIRED_KEYS[@]}"; do
        yq_path="${entry%%|*}"
        display_name="${entry##*|}"

        value=$(yq -e -r "$yq_path" "$FILE" 2>/dev/null) || value=""

        if [ -z "$value" ] || [ "$value" = "null" ]; then
            echo "  MISSING: ${display_name}"
            ENV_MISSING="${ENV_MISSING}\n  - ${display_name}"
            HAS_ERROR=true
        fi
    done

    if [ -n "$ENV_MISSING" ]; then
        MISSING_SUMMARY="${MISSING_SUMMARY}\n[${env}]${ENV_MISSING}"
    fi
done

if [ "$HAS_ERROR" = true ]; then
    echo ""
    echo "ERROR: 시크릿 서브모듈에 필수 키가 누락되어 있습니다."
    echo -e "\n누락 키 요약:${MISSING_SUMMARY}"
    echo ""
    echo "config/secret 레포에 누락된 값을 추가한 후 서브모듈을 업데이트하세요."
    exit 1
fi

echo "OK: 모든 시크릿 키가 정상적으로 설정되어 있습니다."
