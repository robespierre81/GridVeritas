# GridVeritas UI

React (Vite) management console for the GridVeritas core.

## Features (v1)

- Sources: list + create
- Attestations: submit + list by source
- Verify: check payload hash
- Audit: placeholder view

## Local development

```bash
npm install
npm run dev
```

Opens on http://localhost:3000  
API calls are proxied to http://localhost:8080 (see `vite.config.js`).

## Docker

```bash
docker build -t gridveritas/ui:0.1.0 -t gridveritas/ui:latest .
docker run --rm -p 3000:80 gridveritas/ui:latest
```

Inside Docker, `/api/` is proxied to the host/service name `gridveritas-core:8080` (see `nginx/default.conf`).  
Adjust the proxy target for your Compose / network setup.

## Jenkins

Use the included `Jenkinsfile`. It builds the multi-stage Docker image (`gridveritas/ui:<BUILD_NUMBER>` and `:latest`).

The Jenkins agent needs Docker available (same as for the core project).
