#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SECRET_DIR="$PROJECT_ROOT/config/secret"
RESOURCES_DIR="$PROJECT_ROOT/src/main/resources"

echo "==> Initializing secret submodule..."

cd "$PROJECT_ROOT"
git submodule update --init --recursive

if [ ! -d "$SECRET_DIR" ] || [ -z "$(ls -A "$SECRET_DIR" 2>/dev/null)" ]; then
    echo "ERROR: Secret submodule is empty. Check your access to depromeet/18th-team2-server-secret."
    exit 1
fi

echo "==> Creating symlinks for secret config files..."

for file in "$SECRET_DIR"/*.yml "$SECRET_DIR"/*.yaml; do
    [ -f "$file" ] || continue
    filename="$(basename "$file")"
    target="$RESOURCES_DIR/$filename"

    if [ -L "$target" ]; then
        echo "  Symlink already exists: $filename"
    elif [ -f "$target" ]; then
        echo "  WARNING: $filename already exists as a regular file, skipping"
    else
        ln -s "../../../config/secret/$filename" "$target"
        echo "  Created symlink: $filename"
    fi
done

echo "==> Done! Secret configuration is ready."
