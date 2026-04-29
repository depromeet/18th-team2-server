#!/usr/bin/env bash
set -euo pipefail

SECRET_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/config/secret"
BASE_FILE="$SECRET_DIR/application-secret.yml"

if [ ! -f "$BASE_FILE" ]; then
    echo "FAIL: application-secret.yml (기준 파일)이 존재하지 않습니다."
    exit 1
fi

# application-secret.yml에서 leaf 키 경로를 모두 추출
BASE_KEYS=$(yq '.. | select(tag != "!!map" and tag != "!!seq") | path | join(".")' "$BASE_FILE" | sort)

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
    TARGET_KEYS=$(yq '.. | select(tag != "!!map" and tag != "!!seq") | path | join(".")' "$FILE" | sort)
    ENV_MISSING=""

    while IFS= read -r key; do
        if ! echo "$TARGET_KEYS" | grep -qxF "$key"; then
            echo "  MISSING: $key"
            ENV_MISSING="${ENV_MISSING}\n  - ${key}"
            HAS_ERROR=true
        fi
    done <<< "$BASE_KEYS"

    if [ -n "$ENV_MISSING" ]; then
        MISSING_SUMMARY="${MISSING_SUMMARY}\n[${env}]${ENV_MISSING}"
    fi
done

if [ "$HAS_ERROR" = true ]; then
    echo ""
    echo "ERROR: 시크릿 서브모듈에 필수 키가 누락되어 있습니다."
    echo -e "\n누락 키 요약:${MISSING_SUMMARY}"
    echo ""
    echo "application-secret.yml 기준으로 dev/prod에 누락된 키를 추가하세요."
    exit 1
fi

echo "OK: 모든 시크릿 키가 정상적으로 설정되어 있습니다."
