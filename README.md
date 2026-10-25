# RentManager Backend

<div align="center">

**A multi-tenant property-management API for Kenyan rental workflows, built with Java and Spring Boot.**

[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.2.5](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

[Architecture](#architecture) - [Capabilities](#capabilities) - [Run locally](#run-locally) - [API docs](#api-documentation) - [Deployment](#deployment)

</div>

---

## Why this project exists

Rent collection and property operations are often fragmented across mobile-money confirmations, spreadsheets, chat threads, and paper records. RentManager brings the essential workflow into one backend: property and unit management, leases, rental charges and payments, tenant self-service, maintenance, notifications, and operational controls.

The product is designed around Kenya-specific payment workflows, including Safaricom M-Pesa Daraja integration, while keeping the core domain modular enough to evolve independently.

## At a glance

| Area | Evidence in this repository |
| --- | --- |
| Product scope | 20 domain modules covering property, leasing, rent ledger, deposits, maintenance, subscriptions, reviews, tax, and platform administration |
| Data evolution | 100 Flyway migration files; the latest is `V105` |
| Security model | Clerk JWT verification, role-based authorization, tenant-aware request context, callback-secret verification, and rate limiting for public reservation flows |
| Financial safeguards | `BigDecimal` amounts, database constraints, idempotency keys, append-only rent transactions, reconciliation jobs, and pessimistic row locks on sensitive paths |
| Operations | Docker-based local PostgreSQL, Actuator health probes, Prometheus metrics, graceful shutdown, and a Render deployment blueprint |

## Capabilities

### Property operations

- Manage properties, units, media, occupancy, and public listings.
- Create and operate lease lifecycles, including scheduled activation and expiry work.
- Create rental charges, review ledger entries, record payment activity, manage commission policies, and initiate disbursements.
- Manage deposits, tenant branding, payout destinations, subscription plans, and selected provider integrations.
- Coordinate maintenance requests and attachments, announcements, activity history, and renter relationships.

### Renter experience

- Discover publicly listed units and submit reservation requests.
- Access a tenant portal for lease and payment workflows.
- Make M-Pesa payment requests where the relevant payment configuration is enabled.
- Submit maintenance requests, manage devices and notification preferences, and leave reviews where permitted.

### Platform and integrations

- Clerk-based identity, onboarding, account-deletion webhooks, and RBAC.
- M-Pesa Daraja flows for rent, deposit-refund, subscription, and disbursement scenarios.
- Cloudinary media integration and tenant-level integration configuration.
- Notification delivery workflows for push, email, SMS (Africa's Talking), and WhatsApp adapters.
- Platform administration for reviews, settings, commissions, and integrations.

> Third-party functions depend on the relevant credentials and callback configuration. Media and notification providers are optional at startup. Daraja consumer credentials are optional after its encryption key is configured; those capabilities fail closed until configured.

## Architecture

RentManager is a **modular monolith**. It keeps the operational simplicity and transaction guarantees of one deployable while organizing business responsibilities into separate modules. Most modules use an `api` -> `application` -> `domain` -> `infrastructure` shape, separating HTTP concerns, use-case coordination, business logic, and persistence or provider adapters.

```text
Web / Mobile clients
        |
        v
Spring MVC API -- Clerk JWT validation --> tenant context + role checks
        |
        v
Application services --> domain models --> repositories / provider adapters
        |                                      |
        +-------- PostgreSQL + Flyway ----------+
        |
        +---- scheduled processing ------> M-Pesa / media / notifications
```

| Concern | Modules |
| --- | --- |
| Identity and tenancy | `identity`, `tenant`, `user`, `platformadmin`, `platformsettings` |
| Property operations | `property`, `unit`, `lease`, `reservation`, `maintenance`, `deposit` |
| Money and billing | `rentledger`, `tax` |
| Communication and trust | `announcement`, `notification`, `activity`, `audit`, `review` |
| Integrations and clients | `integration`, `mobile` |

The boundary-first design avoids premature microservice overhead while leaving clear seams for future extraction if a module develops independent scaling or operational needs.

## Engineering highlights

### Multi-tenant security

- Clerk JWTs are validated against a JWKS endpoint before request identity is established.
- The authenticated identity resolves to a local tenant context used by tenant-aware application and persistence code.
- The authorization model supports landlord owner, manager, staff, renter, pending-onboarding, and platform-level roles.
- A cleanup filter prevents request-scoped tenant data from leaking between requests.
- Public reservation endpoints use a sliding-window rate limiter and proxy-aware client-address handling.

### Financial correctness

Money-handling code receives more than ordinary CRUD treatment:

- Monetary values use `BigDecimal`, and API responses serialize `BigDecimal` as strings to avoid JavaScript precision loss.
- Rent transaction migrations introduce reversal handling, an append-only database guard, money check constraints, and an idempotency key.
- The rent-ledger repository can acquire a `PESSIMISTIC_WRITE` lock before sensitive state changes.
- Scheduled reconciliation and retry jobs provide controls for payment and disbursement flows.
- Tenant Daraja credentials and platform integration credentials are encrypted with AES-GCM using an application-provided key.

### Reliable asynchronous work

Notification and announcement delivery records are persisted with the business workflow and drained by scheduled processing. This transactional-outbox approach avoids adding a broker before the product has a demonstrated need for one, while retaining a durable retry path for asynchronous work.

### Operational readiness

- Flyway owns schema changes; JPA DDL generation is disabled.
- PostgreSQL 16 is provided through Docker Compose for local development.
- Actuator exposes liveness, readiness, health, and Prometheus metrics on a separate management port.
- The application is configured for graceful shutdown so in-flight work can finish during deploys.
- `render.yaml` supplies a deployable Docker blueprint with production safety settings and documented secrets.

## Technology stack

| Layer | Technology |
| --- | --- |
| Language and framework | Java 21, Spring Boot 3.2.5, Spring MVC, Spring WebFlux |
| Data | PostgreSQL 16, Spring Data JPA, Hibernate, Flyway |
| Security | Spring Security, OAuth2 Resource Server, Clerk JWT/JWKS, JJWT |
| API and validation | Springdoc OpenAPI, Jakarta Validation, MapStruct |
| Payments and integrations | Safaricom Daraja, Cloudinary, Africa's Talking, Expo push adapters |
| Testing | JUnit 5, Spring Boot Test, Testcontainers, Spring Security Test, ArchUnit dependencies |
| Operations | Docker Compose, Maven, Actuator, Micrometer Prometheus, Render |

## API documentation

When API documentation is enabled (it is enabled by default locally), start the application and open:

```text
http://localhost:8080/swagger-ui/index.html
```

The OpenAPI document is generated from the controllers, so it is the source of truth for routes, request models, response models, and authorization requirements. In production, set `API_DOCS_ENABLED=false` to disable public API discovery.

Representative route groups include:

```text
/api/v1/properties                 property and media management
/api/v1/units                      unit operations and media
/api/v1/leases                     lease lifecycle
/api/v1/rent-ledger                rent charges, payments, queries, and unmatched payments
/api/v1/deposits                   deposit lifecycle and refunds
/api/v1/maintenance                maintenance requests and attachments
/api/v1/announcements              tenant communications
/api/v1/tenant-portal              renter-facing operations
/api/v1/tenants/subscription       landlord subscription billing
/api/v1/public/*                   selected listings, configuration, plans, and payment callbacks
```

Protected endpoints expect a Clerk bearer token. Tenant scope is derived from the authenticated identity, not from client-supplied IDs.

## Run locally

### Prerequisites

- JDK 21
- Maven 3.9+
- Docker Desktop or another Docker-compatible runtime
- A Clerk development instance for authenticated workflows

### 1. Clone and start PostgreSQL

```bash
git clone https://github.com/hassan200503/rentmanager-backend.git
cd rentmanager-backend
docker compose up -d postgres
```

The Compose service starts PostgreSQL 16 at `localhost:5432` with the local development database, user, and password configured in `docker-compose.yml`.

### 2. Configure local environment variables

The repository uses `spring-dotenv`, so place local-only values in a git-ignored `.env` file or configure them in your IDE/shell. Never commit real secrets.

```dotenv
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:postgresql://localhost:5432/rentmanager
DB_USERNAME=rentmanager
DB_PASSWORD=rentmanager
JWT_SECRET=replace-with-at-least-32-random-characters
DARAJA_CREDENTIALS_ENCRYPTION_KEY=replace-with-a-base64-encoded-32-byte-key

# Clerk development instance for authenticated endpoints
CLERK_JWKS_URL=https://<your-clerk-instance>/.well-known/jwks.json
CLERK_SECRET_KEY=sk_test_...
CLERK_WEBHOOK_SECRET=whsec_...

# Optional providers
# CLOUDINARY_CLOUD_NAME=...
# CLOUDINARY_API_KEY=...
# CLOUDINARY_API_SECRET=...
# DARAJA_CONSUMER_KEY=...
# DARAJA_CONSUMER_SECRET=...
# DARAJA_CALLBACK_SECRET=...
# AT_ENABLED=true
# AT_API_KEY=...
```

Generate local secrets, for example:

```bash
openssl rand -hex 32
openssl rand -base64 32
```

### 3. Build, test, and run

```bash
mvn clean test
mvn spring-boot:run
```

Flyway applies migrations at startup. Once the API is running:

```bash
curl http://localhost:8080/api/v1/public/mobile/config
curl http://localhost:8081/actuator/health
curl -i http://localhost:8080/api/v1/users/me/access
```

The final command should return `401` without a bearer token.

## Testing strategy

Unit tests exercise domain and application behaviour, while integration tests cover database and tenant-isolation concerns with a real PostgreSQL instance through Testcontainers.

```bash
# Regular test suite
mvn clean test

# Full verification, including Failsafe *IT.java tests
mvn clean verify

# One test class
mvn test -Dtest=ClassName
```

Docker must be running for Testcontainers-backed tests. For high-risk changes, prefer `mvn clean verify`: it includes the integration-test phase and is the appropriate confidence check for tenant isolation, database migrations, callbacks, and cross-module transaction changes.

## Deployment

The repository includes a Dockerfile and [`render.yaml`](render.yaml) blueprint. It runs the container in a production profile and keeps health checks on the public application port while management endpoints remain on a separate internal port.

The minimum production configuration includes:

```dotenv
DB_URL=jdbc:postgresql://<host>:5432/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
JWT_SECRET=<strong-random-secret>
DARAJA_CREDENTIALS_ENCRYPTION_KEY=<stable-base64-encoded-32-byte-key>
CLERK_JWKS_URL=https://<your-clerk-instance>/.well-known/jwks.json
CLERK_SECRET_KEY=<clerk-secret>
CLERK_WEBHOOK_SECRET=<clerk-webhook-secret>
CORS_ALLOWED_ORIGINS=https://<frontend-origin>
```

Configure Daraja, Cloudinary, SMS, email, and push only for the capabilities you enable. See [`docs/deployment.md`](docs/deployment.md) for provider-specific configuration and health checks.

## Repository guide

```text
src/main/java/com/rentmanager/
--- modules/      business modules and provider adapters
--- shared/       security, configuration, errors, web utilities, and money helpers
--- RentManagerApplication.java

src/main/resources/
--- application.yml            runtime and operations configuration
--- db/migration/              versioned Flyway SQL migrations

src/test/java/                 unit, security, integration, isolation, and lifecycle tests
deploy/                        deployment helper scripts and runbooks
docs/                          environment and operational documentation
```

## Engineering decisions worth discussing

1. **Modular monolith over early microservices** - clear domain boundaries and ACID transactions without distributed-systems overhead.
2. **Durable scheduled workflows over an unused broker** - a transactional outbox for notification work, with a clear future migration path if volume warrants it.
3. **Database-supported financial invariants** - application logic is backed by migrations, constraints, locks, and append-only guards rather than controller checks alone.
4. **Provider capability is optional, not silently mocked** - integrations are configured per environment and fail closed when required credentials are absent.
5. **Production safeguards are part of configuration** - API documentation can be disabled, health and metrics are separated, trusted proxies are explicit, and deployment-strict checks are available.

## Project status

RentManager is actively evolving. The public repository is a technical portfolio piece as well as an application codebase; feedback on the architecture, payment workflows, tenant isolation, and operational design is welcome.

For collaboration or engineering opportunities, contact the project owner through [GitHub @hassan200503](https://github.com/hassan200503) or open a repository issue with relevant context.

---

Built by [Hassan](https://github.com/hassan200503) - Java - Spring Boot - PostgreSQL - M-Pesa
