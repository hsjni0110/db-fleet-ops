FROM golang:1.24-alpine AS builder

WORKDIR /src

COPY go.mod go.sum ./

RUN go mod download

COPY . .

RUN go build \
    -o /out/db-fleet-agent \
    ./cmd/db-fleet-agent

FROM alpine:3.20

RUN apk add --no-cache \
    mysql-client \
    ca-certificates \
    tzdata

WORKDIR /app

COPY --from=builder /out/db-fleet-agent /app/db-fleet-agent

RUN mkdir -p /var/lib/db-fleet-agent/backups

ENV CONTROL_PLANE_URL=http://db-fleetops-api:8080
ENV AGENT_NAME=container-agent
ENV AGENT_VERSION=0.1.0
ENV AGENT_STATE_FILE=/var/lib/db-fleet-agent/agent-state.json
ENV BACKUP_DIRECTORY=/var/lib/db-fleet-agent/backups

VOLUME ["/var/lib/db-fleet-agent"]

ENTRYPOINT ["/app/db-fleet-agent"]