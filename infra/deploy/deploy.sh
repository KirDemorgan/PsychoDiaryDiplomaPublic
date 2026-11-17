#!/bin/sh
# Forced SSH command of the deploy user: `status` prints the deployed commit,
# `deploy <sha>` loads a gzip image tar from stdin and restarts the backend.
set -eu

APP_DIR=/opt/diary
HEALTH_URL=http://127.0.0.1:8080/actuator/health
cd "$APP_DIR"

set -- ${SSH_ORIGINAL_COMMAND:-}
action=${1:-}
sha=${2:-}

valid_sha() {
    [ "${#1}" -eq 40 ] && case "$1" in *[!0-9a-f]*) return 1 ;; esac
}

set_tag() {
    grep -v '^BACKEND_TAG=' .env > .env.tmp || true
    echo "BACKEND_TAG=$1" >> .env.tmp
    chmod 600 .env.tmp
    mv .env.tmp .env
}

wait_healthy() {
    for _ in $(seq 1 30); do
        if wget -qO- "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 3
    done
    return 1
}

previous=$(cat DEPLOYED_SHA 2>/dev/null || true)

case "$action" in
    status)
        echo "$previous"
        ;;
    deploy)
        valid_sha "$sha" || { echo "invalid sha" >&2; exit 2; }
        docker load > /dev/null
        docker image inspect "diary-backend:$sha" > /dev/null
        set_tag "$sha"
        docker compose up -d --no-deps backend
        if wait_healthy; then
            echo "$sha" > DEPLOYED_SHA
            docker images diary-backend --format '{{.Tag}}' \
                | grep -v -e "^$sha\$" -e "^${previous:-none}\$" \
                | xargs -r -I{} docker rmi "diary-backend:{}" > /dev/null 2>&1 || true
            echo "deployed $sha"
        else
            echo "health check failed" >&2
            if [ -n "$previous" ]; then
                set_tag "$previous"
                docker compose up -d --no-deps backend
                echo "rolled back to $previous" >&2
            fi
            exit 1
        fi
        ;;
    *)
        echo "unknown command" >&2
        exit 2
        ;;
esac
