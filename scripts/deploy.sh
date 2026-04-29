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

# 컨테이너 healthcheck 대기
wait_healthy() {
    local compose_files="$1"
    local service="$2"
    local max_attempts=10
    local interval=10

    echo "==> Waiting for $service to become healthy..."
    for i in $(seq 1 $max_attempts); do
        status=$(docker compose $compose_files ps "$service" --format '{{.Health}}' 2>/dev/null || echo "unknown")
        if [ "$status" = "healthy" ]; then
            echo "==> $service is healthy!"
            return 0
        elif [ "$status" = "unhealthy" ]; then
            echo "ERROR: $service is unhealthy. Dumping logs:"
            docker compose $compose_files logs --tail=50 "$service"
            return 1
        fi
        echo "  [$i/$max_attempts] status=$status, waiting ${interval}s..."
        sleep "$interval"
    done

    echo "ERROR: $service failed to become healthy within $((max_attempts * interval))s. Dumping logs:"
    docker compose $compose_files logs --tail=50 "$service"
    return 1
}

# 공유 네트워크 생성
for network in dev-network prod-network; do
    if ! docker network inspect "$network" &>/dev/null; then
        echo "==> Creating network: $network"
        docker network create "$network"
    fi
done

if [ "$ENV" = "dev" ] || [ "$ENV" = "all" ]; then
    DEV_FILES="-f docker-compose.yml -f docker-compose.dev.yml"
    echo "==> Building and starting DEV containers..."
    docker compose $DEV_FILES up -d --build
    wait_healthy "$DEV_FILES" "app-dev"
fi

if [ "$ENV" = "prod" ] || [ "$ENV" = "all" ]; then
    PROD_FILES="-f docker-compose.yml -f docker-compose.prod.yml"
    echo "==> Building and starting PROD containers..."
    docker compose $PROD_FILES up -d --build
    wait_healthy "$PROD_FILES" "app-prod"
fi

echo "==> Done!"
