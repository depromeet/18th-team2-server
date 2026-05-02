#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

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

echo "==> Stopping nginx..."
docker compose $NGINX_FILES down --remove-orphans

echo "==> Starting nginx..."
docker compose $NGINX_FILES up -d

if ! docker compose $NGINX_FILES ps --status running --services | grep -qx nginx; then
    echo "ERROR: nginx is not running. Dumping logs:"
    docker compose $NGINX_FILES logs --tail=50 nginx
    exit 1
fi

echo "==> Done!"
