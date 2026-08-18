# GridVeritas – Isolated Docker Stack

Everything runs on a **dedicated Docker network** (`gridveritas-net`) so it does not collide with your other containers.

## Services

| Service        | Container name          | Host port | Internal |
|-----------------|--------------------------|-----------|----------|
| UI              | `gridveritas-ui`        | **3080**  | 80       |
| Traefik (LB)    | `gridveritas-traefik`   | **18080** | 80       |
| Core API        | *(scalable, no fixed name)* | *(none)*  | 8080     |
| PostgreSQL primary | `gridveritas-postgres` | *(none)* | 5432 |
| PostgreSQL replica | `gridveritas-postgres-replica` | *(none)* | 5432 |
| PostgreSQL write proxy | `gridveritas-postgres-rw` | *(none)* | 5432 |
| Redis           | `gridveritas-redis`     | *(none)*  | 6379     |
| Prometheus      | `gridveritas-prometheus`| **19090** | 9090     |
| Grafana         | `gridveritas-grafana`   | **33000** | 3000     |

Postgres and Redis are **not** published on the host (only reachable inside
`gridveritas-net`). The app talks to `postgres-rw` (HAProxy). A streaming
replica and a promote watcher cover a **primary-container crash** on this
host; they do not survive host death and do not claim 99.9 % (ADR-015).
Core has no fixed container name or host port because it can run as multiple
replicas (`docker compose up -d --scale core=2`, ADR-013) — Traefik is the
single stable entrypoint, now publishing the port core used to publish
directly.

## Start

```bash
cd /path/to/artifacts   # folder that contains docker-compose.yml, gridveritas-core/, gridveritas-ui/
docker compose up -d --build
```

Or:

```bash
./gridveritas-up.sh
```

## Stop

```bash
docker compose down
# or remove DB data as well:
docker compose down -v
```

## Access

- **UI:** http://localhost:3080  
- **API (via Traefik):** http://localhost:18080/api/v1/sources  
- **Grafana:** http://localhost:33000  (admin / `GRAFANA_ADMIN_PASSWORD`, default `admin-change-me`)  
- **Prometheus:** http://localhost:19090  

Core scrapes are `GET /actuator/prometheus` on each replica (permit-all, not rate-limited). Prometheus discovers replicas via Docker DNS (`core:8080`), so `--scale core=N` is picked up automatically. Grafana ships a provisioned “GridVeritas overview” dashboard (JVM, HTTP, HikariCP, instance heartbeats).

Inside the stack, the UI nginx proxies `/api/` → Traefik → whichever core
replica is healthy (round-robin across however many are running). Run
multiple core replicas with `docker compose up -d --scale core=2`; check how
many are actually online at any time via
`GET /api/v1/cluster/instances` (ADR-013).

## Host proof (replica + federation)

These are live-stack checks, not `mvn test`. Jenkins runs the first two on every build; failover is opt-in (`RUN_DB_FAILOVER`).

```bash
# replica is a hot standby; postgres-rw is writable
./ci/check_postgres_replica.sh

# publish /info + /roots, then fetch this operator through the LB (loopback peer)
docker run --rm -i --network gridveritas-net \
  -e ADMIN_PASSWORD=admin-change-me \
  -e BASE_URL=http://gridveritas-traefik \
  --entrypoint sh alpine:3.20 -s \
  < ci/check_federation.sh

# destructive: stop the primary and prove promote + write
./ci/prove_postgres_failover.sh
```

## Network isolation

```bash
docker network inspect gridveritas-net
```

Only containers attached to `gridveritas-net` can talk to each other by service name (`core`, `ui`, `postgres` / `postgres-primary`, `postgres-rw`, `redis`, `traefik`). Other stacks on the same host are unaffected.

## Change host ports

Edit `ports:` in `docker-compose.yml` if 3080 or 18080 are already in use on your machine.
