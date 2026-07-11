# M4 real-infrastructure smoke test

This module is activated only by the `smoke` Maven profile. It starts pinned PostgreSQL/pgvector,
RabbitMQ, and Redis containers, then starts `core-service`, `identity-service`, and
`gateway-service` on random local ports.

Run from the repository root:

```bash
./scripts/m4-smoke
```

Equivalent Maven command:

```bash
mvn -Psmoke -pl integration-tests/m4-smoke-test -am verify
```

The command requires a working Docker-compatible daemon. Missing Docker is a test failure, not a
skip. Image names can be overridden when a registry mirror is required:

```bash
mvn -Psmoke -pl integration-tests/m4-smoke-test -am verify \
  -Dliteworkflow.smoke.postgres-image=pgvector/pgvector:0.8.2-pg16-trixie \
  -Dliteworkflow.smoke.rabbitmq-image=rabbitmq:4.1.8-management-alpine \
  -Dliteworkflow.smoke.redis-image=redis:8.2.7-alpine3.22
```

The smoke path validates:

1. Real Flyway migrations in the `identity` and `core` PostgreSQL schemas.
2. Registration through Gateway and a real RabbitMQ `identity.user.registered` delivery.
3. Core user-directory projection readiness through its public API.
4. Workspace creation and OWNER authorization.
5. Candidate search through Gateway's real Redis-backed rate limiter.
6. Workspace member add, role change, list, remove, and immediate permission revocation.
7. Real RabbitMQ delivery of all four member events and `PUBLISHED` Core Outbox state.
