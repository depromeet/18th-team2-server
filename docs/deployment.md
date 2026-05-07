# Deployment

이 문서는 dev/prod Docker 배포 구조와 무중단 app 전환 절차를 정리한다. 시크릿 값 관리는 `docs/secret-management.md`를 따른다.

## Environments

| 환경 | 트리거 | 도메인 | Docker network | DB container | App slots |
| --- | --- | --- | --- | --- | --- |
| dev | `develop` push | `dev-api.hapalin.com`, `:8081` | `dev-network` | `team2-db-dev` | `team2-app-dev-blue`, `team2-app-dev-green` |
| prod | `main` push | `api.hapalin.com` | `prod-network` | `team2-db-prod` | `team2-app-prod-blue`, `team2-app-prod-green` |

`team2-nginx`는 `dev-network`와 `prod-network`에 모두 붙어 있으며, active app slot으로 요청을 프록시한다.

## Deployment Flow

`scripts/deploy.sh dev|prod`는 DB를 내리지 않고 app slot만 전환한다.

1. secret submodule에서 DB 값을 읽어 `.env`를 생성한다.
2. `dev-network`와 `prod-network`가 없으면 생성한다.
3. 대상 환경의 DB 컨테이너를 `up -d`로 보장하고 healthy 상태를 기다린다.
4. 현재 active slot을 `.deploy-state/{env}-active-slot`, 실행 중인 blue/green 컨테이너, legacy 컨테이너 순서로 감지한다.
5. inactive slot을 `docker/docker-compose.app.yml`로 빌드하고 기동한다.
6. 새 app container의 `/actuator/health`가 healthy가 될 때까지 기다린다.
7. ignored runtime 파일인 `nginx/conf.d/team2-active-upstreams.conf`를 새 slot으로 렌더링한다.
8. `team2-nginx`에서 `nginx -t`를 실행하고 성공하면 `nginx -s reload`로 upstream을 전환한다.
9. nginx 경유 health endpoint를 확인한다.
10. 전환이 성공하면 이전 app slot 또는 legacy app container를 제거한다.

## Rollback Strategy

새 app slot이 healthy가 되기 전에는 nginx upstream을 변경하지 않는다. 이 경우 기존 active slot이 계속 트래픽을 받으므로 별도 rollback 없이 배포가 실패한다.

nginx config test, reload, 또는 nginx 경유 health verification이 실패하면 `scripts/deploy.sh`는 이전 upstream 파일을 다시 렌더링하고 nginx reload를 재시도한다. 이전 app slot은 전환 성공 전까지 제거하지 않기 때문에 app rollback은 upstream 되돌리기로 처리한다.

수동 rollback이 필요한 경우 `.deploy-state/{env}-active-slot` 값과 실행 중인 slot을 확인한 뒤, 이전 slot으로 `nginx/conf.d/team2-active-upstreams.conf`를 렌더링하고 `team2-nginx`에서 `nginx -t && nginx -s reload`를 실행한다. DB migration이 이미 적용된 경우에는 app rollback만으로 해결하지 않고, migration 호환성 또는 보정 migration을 별도로 판단한다.

다른 환경에 실행 중인 app slot이 없으면 nginx upstream은 빈 값과 availability flag `0`으로 렌더링된다. 이 경우 해당 도메인은 없는 컨테이너로 프록시하지 않고 503을 반환한다.

## Healthcheck Timing

Blue-green 전환에서는 app을 먼저 띄우고 healthy 이후에만 nginx를 바꾸므로 기존보다 health 대기 시간이 중요하다.

App healthcheck는 `curl -f --max-time 5`와 `interval: 10s`, `timeout: 5s`, `retries: 12`, `start_period: 60s`를 사용한다. 기존 `interval: 30s`보다 readiness 감지가 빠르고, Spring/Flyway 초기화가 느린 배포도 최대 약 3분까지 기다릴 수 있다. 배포 스크립트도 5초 간격으로 최대 180초 동안 새 app container health를 확인한다.

DB healthcheck는 기존 DB container를 유지하는 구조라 배포마다 DB를 재생성하지 않는다. 다만 DB가 내려가 있으면 `up -d` 후 healthy 상태를 기다린다.

## Operational Notes

- dev/prod DB는 각각 단일 컨테이너로 유지한다. 무중단 전환 대상은 app container뿐이다.
- 배포 중에는 대상 환경 app container가 일시적으로 2개 떠 있으므로 환경당 최대 `+1GB`, `+0.7 CPU`의 여유가 필요하다.
- dev와 prod를 동시에 배포하면 app 기준 최대 `+2GB`, `+1.4 CPU`까지 추가될 수 있으므로 서버 여유가 작으면 순차 배포한다.
- app log bind mount는 slot별로 `logs/{env}/{slot}`을 사용한다. `scripts/deploy.sh`가 디렉터리를 만들고, app container를 배포 호스트의 UID/GID(`APP_UID`, `APP_GID`, 기본값 `id -u`, `id -g`)로 실행해 `0775` 권한으로 쓰게 한다.
- `team2-nginx` 설정 자체를 바꾼 경우에는 `scripts/deploy-nginx.sh`를 별도로 실행한다. 이 스크립트도 `.deploy-state`와 실행 중인 컨테이너를 읽어 현재 active slot을 유지하고, 기존 active upstream 파일을 백업한 뒤 compose 설정을 적용한다.
- `nginx/conf.d/team2-active-upstreams.conf`와 `.deploy-state/`는 서버 runtime state라 git에 커밋하지 않는다. `nginx/conf.d/.gitkeep`은 bind mount 디렉터리를 보장하기 위한 placeholder다.
- 배포 호스트에는 `curl`이 필요하다. `scripts/deploy.sh`는 nginx 경유 health verification을 통과하지 못하면 배포를 실패 처리한다.
- schema 변경은 old/new app이 동시에 동작할 수 있게 `expand -> deploy -> contract` 순서로 나눈다. 호환되지 않는 컬럼 삭제, rename, 제약 변경은 app 무중단 배포만으로 보호되지 않는다.

## References

- Docker Compose project name: https://docs.docker.com/compose/how-tos/project-name/
- Docker Compose healthcheck: https://docs.docker.com/reference/compose-file/services/#healthcheck
- nginx reload behavior: https://nginx.org/en/docs/control.html
- nginx command-line `-t` and `-s reload`: https://nginx.org/en/docs/switches.html
