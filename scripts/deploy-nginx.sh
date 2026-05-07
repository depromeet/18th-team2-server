#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NGINX_BACKUP_DIR="$PROJECT_ROOT/.deploy-state/nginx-conf-backup"

cd "$PROJECT_ROOT"
source "$PROJECT_ROOT/scripts/lib-deploy.sh"
ensure_fallback_upstreams

backup_active_upstreams() {
    rm -rf "$NGINX_BACKUP_DIR"
    mkdir -p "$NGINX_BACKUP_DIR"
    cp "$NGINX_CONF_DIR"/team2-active-upstreams-*.conf "$NGINX_BACKUP_DIR"/
}

restore_active_upstreams() {
    if [ -d "$NGINX_BACKUP_DIR" ] && ls "$NGINX_BACKUP_DIR"/team2-active-upstreams-*.conf >/dev/null 2>&1; then
        cp "$NGINX_BACKUP_DIR"/team2-active-upstreams-*.conf "$NGINX_CONF_DIR"/
    else
        render_env_upstream dev none
        render_env_upstream prod none
    fi
}

for network in dev-network prod-network; do
    if ! docker network inspect "$network" &>/dev/null; then
        echo "==> Creating network: $network"
        docker network create "$network"
    fi
done

EXISTING_CONTAINER_ID="$(docker ps -aq -f name='^/team2-nginx$' | head -n 1)"
if [ -n "$EXISTING_CONTAINER_ID" ]; then
    EXISTING_PROJECT="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$EXISTING_CONTAINER_ID" 2>/dev/null || true)"
    if [ "$EXISTING_PROJECT" != "team2-nginx" ]; then
        echo "==> Removing legacy nginx container: team2-nginx"
        docker stop "$EXISTING_CONTAINER_ID" >/dev/null 2>&1 || true
        docker rm "$EXISTING_CONTAINER_ID" >/dev/null
    fi
fi

NGINX_FILES="-f docker/docker-compose.nginx.yml"

echo "==> Rendering active nginx upstreams..."
backup_active_upstreams
render_all_detected_upstreams

if docker ps --filter 'name=^/team2-nginx$' --format '{{.Names}}' | grep -qx 'team2-nginx'; then
    if ! install_nginx_upstreams_for_running_container || ! docker exec team2-nginx nginx -t; then
        echo "ERROR: nginx config test failed before compose update. Restoring previous upstream files."
        restore_active_upstreams
        install_nginx_upstreams_for_running_container || true
        if ! docker exec team2-nginx nginx -s reload; then
            echo "CRITICAL: nginx rollback reload also failed. Manual intervention required."
        fi
        exit 1
    fi
fi

echo "==> Applying nginx compose configuration..."
if ! docker compose $NGINX_FILES up -d --remove-orphans; then
    echo "ERROR: failed to apply nginx compose configuration. Restoring previous upstream files."
    restore_active_upstreams
    docker compose $NGINX_FILES up -d || true
    docker compose $NGINX_FILES logs --tail=50 nginx
    exit 1
fi

if ! docker compose $NGINX_FILES ps --status running --services | grep -qx nginx; then
    echo "ERROR: nginx is not running. Restoring previous upstream files."
    restore_active_upstreams
    docker compose $NGINX_FILES up -d || true
    docker compose $NGINX_FILES logs --tail=50 nginx
    exit 1
fi

if ! docker compose $NGINX_FILES exec -T nginx nginx -t; then
    echo "ERROR: nginx config test failed. Restoring previous upstream files."
    restore_active_upstreams
    docker compose $NGINX_FILES up -d || true
    docker compose $NGINX_FILES logs --tail=50 nginx
    exit 1
fi

docker compose $NGINX_FILES exec -T nginx nginx -s reload
rm -rf "$NGINX_BACKUP_DIR"

echo "==> Done!"
