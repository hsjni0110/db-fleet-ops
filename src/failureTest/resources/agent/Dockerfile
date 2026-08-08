FROM golang:1.24-alpine AS builder
WORKDIR /src
COPY agent-go/go.mod agent-go/go.sum ./
RUN go mod download
COPY agent-go/ ./
RUN CGO_ENABLED=0 GOOS=linux go build -o /out/db-fleet-agent ./cmd/db-fleet-agent

FROM mysql:8.0.39
COPY --from=builder /out/db-fleet-agent /usr/local/bin/db-fleet-agent
COPY src/failureTest/resources/agent/mysqldump /failure-bin/mysqldump
RUN chmod 755 /failure-bin/mysqldump /usr/local/bin/db-fleet-agent
ENV PATH="/failure-bin:${PATH}"
ENTRYPOINT ["/usr/local/bin/db-fleet-agent"]
