Public deposit: https://doi.org/10.5281/zenodo.21820827 — source https://github.com/robespierre81/GridVeritas (`docs-v1.4`).

# GridVeritas – Isolated Docker Stack

Everything runs on a **dedicated Docker network** (`gridveritas-net`) so it does not collide with your other containers.

## Services

| Service   | Container name         | Host port | Internal |
|-----------|------------------------|-----------|----------|
| UI        | `gridveritas-ui`       | **3080**  | 80       |
| Core API  | `gridveritas-core`     | **18080** | 8080     |
| PostgreSQL| `gridveritas-postgres` | *(none)*  | 5432     |

Postgres is **not** published on the host by default (only reachable inside `gridveritas-net`).

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
- **API (direct):** http://localhost:18080/api/v1/sources  

Inside the stack, the UI nginx proxies `/api/` → `http://gridveritas-core:8080/api/`.

## Network isolation

```bash
docker network inspect gridveritas-net
```

Only containers attached to `gridveritas-net` can talk to each other by service name (`core`, `ui`, `postgres`). Other stacks on the same host are unaffected.

## Change host ports

Edit `ports:` in `docker-compose.yml` if 3080 or 18080 are already in use on your machine.
