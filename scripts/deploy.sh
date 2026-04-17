#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ENV="${1:-}"

if [ -z "$ENV" ] || [[ ! "$ENV" =~ ^(dev|prod|all)$ ]]; then
    echo "Usage: ./scripts/deploy.sh [dev|prod|all]"
    exit 1
fi

cd "$PROJECT_ROOT"

echo "==> Updating submodules..."
git submodule update --init --recursive

SECRET_DIR="$PROJECT_ROOT/config/secret"

# yq 설치 확인
if ! command -v yq &> /dev/null; then
    echo "ERROR: yq가 설치되어 있지 않습니다. https://github.com/mikefarah/yq 참고"
    exit 1
fi

# 서브모듈 secret yml에서 DB 정보를 파싱하여 .env 생성
parse_db_info() {
    local file="$1"
    local prefix="$2"

    local username
    username=$(yq -e -r '.spring.datasource.username' "$file") || { echo "ERROR: username 파싱 실패 ($file)"; exit 1; }

    local password
    password=$(yq -e -r '.spring.datasource.password' "$file") || { echo "ERROR: password 파싱 실패 ($file)"; exit 1; }

    local root_password
    root_password=$(yq -e -r '.spring.datasource.root-password // .spring.datasource.password' "$file") || { echo "ERROR: root-password 파싱 실패 ($file)"; exit 1; }

    echo "${prefix}_DB_USERNAME=$username"
    echo "${prefix}_DB_PASSWORD=$password"
    echo "${prefix}_DB_ROOT_PASSWORD=$root_password"
}

echo "==> Generating .env from secret submodule..."
umask 077
> "$PROJECT_ROOT/.env"

if [ "$ENV" = "dev" ] || [ "$ENV" = "all" ]; then
    parse_db_info "$SECRET_DIR/application-secret-dev.yml" "DEV" >> "$PROJECT_ROOT/.env"
fi

if [ "$ENV" = "prod" ] || [ "$ENV" = "all" ]; then
    parse_db_info "$SECRET_DIR/application-secret-prod.yml" "PROD" >> "$PROJECT_ROOT/.env"
fi

# 공유 네트워크 생성
for network in dev-network prod-network; do
    if ! docker network inspect "$network" &>/dev/null; then
        echo "==> Creating network: $network"
        docker network create "$network"
    fi
done

if [ "$ENV" = "dev" ] || [ "$ENV" = "all" ]; then
    echo "==> Building and starting DEV containers..."
    docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
fi

if [ "$ENV" = "prod" ] || [ "$ENV" = "all" ]; then
    echo "==> Building and starting PROD containers..."
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
fi

echo "==> Done! Checking container status..."
if [ "$ENV" = "dev" ]; then
    docker compose -f docker-compose.yml -f docker-compose.dev.yml ps
elif [ "$ENV" = "prod" ]; then
    docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
else
    docker compose -f docker-compose.yml -f docker-compose.dev.yml -f docker-compose.prod.yml ps
fi
