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
source "$PROJECT_ROOT/scripts/lib-deploy.sh"
ensure_fallback_upstreams

echo "==> Updating submodules..."
git submodule update --init --recursive

SECRET_DIR="$PROJECT_ROOT/config/secret"

if ! command -v yq &> /dev/null; then
    echo "ERROR: yq가 설치되어 있지 않습니다. https://github.com/mikefarah/yq 참고"
    exit 1
fi

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

for network in dev-network prod-network; do
    if ! docker network inspect "$network" &>/dev/null; then
        echo "==> Creating network: $network"
        docker network create "$network"
    fi
done

profiles_for_env() {
    local app_env="$1"
    echo "$app_env,secret-$app_env"
}

db_compose_files() {
    local app_env="$1"
    echo "--env-file .env -f docker/docker-compose.$app_env.yml"
}

db_service() {
    echo "db-$1"
}

app_compose() {
    local app_env="$1"
    local slot="$2"
    shift 2

    local profiles
    profiles="$(profiles_for_env "$app_env")"

    APP_ENV="$app_env" APP_SLOT="$slot" SPRING_PROFILES_ACTIVE="$profiles" \
        docker compose --env-file .env -f docker/docker-compose.app.yml -p "team2-app-$app_env-$slot" "$@"
}

wait_container_healthy() {
    local container="$1"
    local max_attempts=48
    local interval=5
    local status

    echo "==> Waiting for $container to become healthy..."
    for i in $(seq 1 "$max_attempts"); do
        status="$(container_health "$container")"

        if [ "$status" = "healthy" ]; then
            echo "==> $container is healthy!"
            return 0
        elif [ "$status" = "unhealthy" ]; then
            echo "ERROR: $container is unhealthy. Dumping logs:"
            docker logs --tail=80 "$container"
            return 1
        fi

        if [ "$i" -lt "$max_attempts" ]; then
            echo "  [$i/$max_attempts] status=${status:-unknown}, waiting ${interval}s..."
            sleep "$interval"
        fi
    done

    echo "ERROR: $container failed to become healthy within $((max_attempts * interval))s. Dumping logs:"
    docker logs --tail=80 "$container"
    return 1
}

wait_compose_service_healthy() {
    local compose_files="$1"
    local service="$2"
    local max_attempts=24
    local interval=5
    local status

    echo "==> Waiting for $service to become healthy..."
    for i in $(seq 1 "$max_attempts"); do
        if ! status=$(docker compose $compose_files ps "$service" --format '{{.Health}}' 2>/dev/null); then
            echo "ERROR: failed to read health status for $service"
            return 1
        fi
        status=${status:-unknown}

        if [ "$status" = "healthy" ]; then
            echo "==> $service is healthy!"
            return 0
        elif [ "$status" = "unhealthy" ]; then
            echo "ERROR: $service is unhealthy. Dumping logs:"
            docker compose $compose_files logs --tail=80 "$service"
            return 1
        fi

        if [ "$i" -lt "$max_attempts" ]; then
            echo "  [$i/$max_attempts] status=$status, waiting ${interval}s..."
            sleep "$interval"
        fi
    done

    echo "ERROR: $service failed to become healthy within $((max_attempts * interval))s. Dumping logs:"
    docker compose $compose_files logs --tail=80 "$service"
    return 1
}

test_nginx_config() {
    if ! docker ps --filter 'name=^/team2-nginx$' --format '{{.Names}}' | grep -qx 'team2-nginx'; then
        echo "ERROR: team2-nginx is not running"
        return 1
    fi

    install_nginx_upstreams_for_running_container || return 1
    docker exec team2-nginx nginx -t
}

reload_nginx() {
    docker exec team2-nginx nginx -s reload
}

verify_nginx_route() {
    local app_env="$1"

    if ! command -v curl &>/dev/null; then
        echo "ERROR: curl is required on the deployment host for nginx route verification."
        return 1
    fi

    if [ "$app_env" = "dev" ]; then
        curl -fsS --connect-timeout 3 --max-time 10 http://127.0.0.1:8081/actuator/health >/dev/null ||
            curl -kfsS --connect-timeout 3 --max-time 10 --resolve dev-api.hapalin.com:443:127.0.0.1 https://dev-api.hapalin.com/actuator/health >/dev/null
    else
        curl -kfsS --connect-timeout 3 --max-time 10 --resolve api.hapalin.com:443:127.0.0.1 https://api.hapalin.com/actuator/health >/dev/null
    fi
}

switch_nginx_to_slot() {
    local app_env="$1"
    local new_slot="$2"
    local previous_target="$3"

    echo "==> Switching nginx $app_env upstream to $new_slot..."
    render_env_upstream "$app_env" "$new_slot"

    if ! test_nginx_config; then
        echo "ERROR: nginx config test failed. Restoring previous upstream file."
        render_env_upstream "$app_env" "$previous_target"
        install_nginx_upstreams_for_running_container || true
        return 1
    fi

    if ! reload_nginx; then
        echo "ERROR: nginx reload failed. Restoring previous upstream file."
        render_env_upstream "$app_env" "$previous_target"
        install_nginx_upstreams_for_running_container || true
        if ! reload_nginx; then
            echo "CRITICAL: nginx rollback reload also failed. Manual intervention required."
        fi
        return 1
    fi

    if ! verify_nginx_route "$app_env"; then
        echo "ERROR: nginx route verification failed. Rolling back upstream."
        render_env_upstream "$app_env" "$previous_target"
        if ! install_nginx_upstreams_for_running_container; then
            echo "CRITICAL: failed to restore nginx upstream file. Manual intervention required."
            return 1
        fi
        if test_nginx_config; then
            if ! reload_nginx; then
                echo "CRITICAL: nginx rollback reload also failed. Manual intervention required."
            fi
        else
            echo "CRITICAL: restored nginx config failed validation. Manual intervention required."
        fi
        return 1
    fi

    echo "$new_slot" > "$(state_file "$app_env")"
}

cleanup_previous_target() {
    local app_env="$1"
    local previous_target="$2"

    if is_valid_slot "$previous_target"; then
        echo "==> Stopping previous $app_env app slot: $previous_target"
        app_compose "$app_env" "$previous_target" down --remove-orphans
    elif [ "$previous_target" = "legacy" ]; then
        echo "==> Removing legacy $app_env app container..."
        docker rm -f "$(legacy_app_container "$app_env")" 2>/dev/null || true
    fi
}

deploy_environment() {
    local app_env="$1"
    local compose_files
    local service
    local previous_target
    local new_slot
    local new_container

    compose_files="$(db_compose_files "$app_env")"
    service="$(db_service "$app_env")"

    echo "==> Ensuring $app_env database is running..."
    docker compose $compose_files up -d "$service"
    wait_compose_service_healthy "$compose_files" "$service"

    previous_target="$(detect_active_target "$app_env")"
    if is_valid_slot "$previous_target"; then
        new_slot="$(other_slot "$previous_target")"
    else
        new_slot="blue"
    fi
    new_container="$(app_container "$app_env" "$new_slot")"

    echo "==> Current $app_env target: $previous_target"
    echo "==> Building and starting new $app_env app slot: $new_slot"
    app_compose "$app_env" "$new_slot" up -d --build --force-recreate app
    wait_container_healthy "$new_container"

    switch_nginx_to_slot "$app_env" "$new_slot" "$previous_target"
    cleanup_previous_target "$app_env" "$previous_target"

    echo "==> $app_env deployment switched to $new_slot"
}

if [ "$ENV" = "dev" ] || [ "$ENV" = "all" ]; then
    deploy_environment dev
fi

if [ "$ENV" = "prod" ] || [ "$ENV" = "all" ]; then
    deploy_environment prod
fi

echo "==> Done!"
