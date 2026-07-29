# ============================================================
# GridVeritas UI – Multi-stage Dockerfile
# Build with Node, serve with nginx
# ============================================================

# ---------- Build stage ----------
FROM node:20-alpine AS build
WORKDIR /app

COPY package.json ./
RUN npm install

COPY . .
# Optional: bake API base at build time (default uses relative /api via nginx proxy)
ARG VITE_API_BASE=/gridveritas/api/v1
ENV VITE_API_BASE=$VITE_API_BASE
RUN npm run build

# ---------- Runtime stage ----------
FROM nginx:1.27-alpine

LABEL maintainer="Oliver Bodemer" \
      project="GridVeritas" \
      component="ui" \
      version="0.1.0"

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
