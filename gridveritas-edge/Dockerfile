# GridVeritas Edge Agent (Go)
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod ./
RUN go mod download 2>/dev/null || true
COPY . .
RUN CGO_ENABLED=0 go build -o /agent ./cmd/agent

FROM alpine:3.20
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY --from=build /agent /app/agent
ENV GRIDVERITAS_CORE_URL=http://gridveritas-core:8080
ENV GRIDVERITAS_AGENT_NAME=edge-agent-01
ENV GRIDVERITAS_INTERVAL=15s
ENTRYPOINT ["/app/agent"]
