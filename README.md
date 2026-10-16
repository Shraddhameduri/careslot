# CareSlot — Multi-tenant Appointment Booking SaaS

A demo portfolio project: clinics/service businesses sign up as tenants, manage
providers and schedules, and customers book appointments in real time — with
strict double-booking prevention, JWT auth + RBAC, a (mock) Stripe-style payment
flow, and live availability updates over SSE.

**Stack:** Java 17 · Spring Boot 3.2 · Spring Security · Spring Data JPA ·
PostgreSQL 16 · Flyway · Redis (with in-memory fallback) · React 18 +
TypeScript + Vite · Docker Compose · GitHub Actions CI

> **Payment honesty note:** the payment flow is a **mock** (`mock-stripe`
> provider, `MockStripePaymentProvider`). It implements a Stripe-style
> PaymentIntent lifecycle (create → confirm → FAILED/CONFIRMED) with zero
> network calls and no money movement. It is labeled `mock` in code, API paths
> (`/api/v1/payments/mock/...`), and responses (`"provider": "mock-stripe"`).

## Architecture

```mermaid
flowchart LR
    subgraph Client
        FE[React SPA<br/>Vite + TS]
    end
    subgraph Edge
        NG[nginx<br/>static + /api proxy<br/>SSE passthrough]
    end
    subgraph API[Spring Boot 3.2]
        JWT[JwtAuthFilter]
        TEN[TenantContextFilter]
        CTRL[REST controllers]
        BOOK[BookingService<br/>@Transactional]
        SLOT[SlotService]
        AVAIL[AvailabilityService]
        SSE[SseService<br/>fan-out]
        PAY[PaymentService<br/>mock-stripe]
        CACHE[(AvailabilityCache<br/>Redis → in-memory fallback)]
    end
    subgraph Data
        PG[(PostgreSQL 16<br/>Flyway migrations)]
        RD[(Redis 7<br/>availability cache)]
    end
    FE --> NG --> CTRL
    CTRL --> JWT --> TEN --> BOOK
    BOOK --> PG
    BOOK --> PAY
    BOOK --> AVAIL --> CACHE
    AVAIL --> SSE --> FE
    CACHE -.-> RD
```

**Request flow:** `JwtAuthFilter` → `TenantContextFilter` (both inside the
Spring Security chain) → controller → service. Tenancy is resolved from the
JWT claim for authenticated requests, or from the `X-Tenant-Id` header /
`?tenant=<slug>` for public endpoints. Every repository query is tenant-scoped.

## Quickstart

### Option A — Docker Compose (Postgres + Redis + backend + frontend)

```bash
cd careslot
docker compose up --build
# frontend: http://localhost:3000   backend: http://localhost:8080
```

The backend seeds a demo tenant on first boot (`SEED_ENABLED=true`):
tenant slug `sunrise-dental`, 3 services, 1 provider (Mon–Fri 9:00–17:00),
and 14 days of 30-minute slots.

**Demo logins** (seed data only): `admin@careslot.demo`,
`provider@careslot.demo`, `customer@careslot.demo` — password `Password123!`
for all three.

### Option B — local dev

```bash
# backend (needs Java 17 + Maven; Postgres + Redis optional — see fallback note)
cd backend && mvn spring-boot:run
# frontend (Vite dev server proxies /api to localhost:8080)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

**Redis is optional.** If Redis is unreachable at startup, the app logs
`using in-memory availability cache fallback` and runs fully on a 60s-TTL
in-memory cache. The test suite exercises this path (no Redis in tests).

## API reference

Base: `/api/v1`. Auth: `Authorization: Bearer <accessToken>`.
Public endpoints need `X-Tenant-Id` header or `?tenant=<slug>`.

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/signup-tenant` | public, rate-limited | Create tenant + tenant-admin; returns tokens |
| POST | `/auth/register` | public, rate-limited | Customer self-registration (tenant header) |
| POST | `/auth/login` | public, rate-limited | Login (tenant header) |
| POST | `/auth/refresh` | public, rate-limited | Rotate refresh token |
| POST | `/auth/logout` | — | Revoke refresh token |
| GET | `/public/tenants/{slug}` | public | Tenant info |
| GET | `/public/services?tenant=` | public | Active services |
| GET | `/public/providers?tenant=` | public | Active providers |
| GET | `/public/availability?providerId=&date=` | public | Slots for a day (cached) |
| GET | `/public/availability/stream?providerId=&date=` | public | SSE: live slot updates |
| POST | `/bookings` | CUSTOMER | Book a slot (mock payment inside) → 201 / 409 |
| GET | `/bookings/mine` | CUSTOMER | My bookings |
| POST | `/bookings/{id}/cancel` | owner/provider/admin | Cancel, frees the slot |
| GET/PUT | `/provider/schedule` | PROVIDER | Working hours (1=Mon..7=Sun) |
| POST | `/provider/slots/generate` | PROVIDER | Generate slots from hours |
| GET | `/provider/appointments?date=` | PROVIDER | Day's appointments |
| GET/POST/PUT/DELETE | `/admin/services` | TENANT_ADMIN | Service catalog |
| GET/POST | `/admin/providers` | TENANT_ADMIN | Providers (+ login user) |
| GET | `/admin/appointments?date=` | TENANT_ADMIN | All appointments |
| POST | `/admin/providers/{id}/slots/generate` | TENANT_ADMIN | Generate for a provider |
| POST | `/payments/mock/intents?amountCents=` | auth | Mock PaymentIntent |
| POST | `/payments/mock/intents/{ref}/confirm` | auth | `{succeed:true/false}` |

Errors are JSON: `{"error","message","status"}` — 400/401/403/404/409/429.

## Design decisions

### Double-booking prevention (two layers)

1. **Transactional layer** (`BookingService.book`, `@Transactional`):
   the slot row is locked with `SELECT … FOR UPDATE` and its status
   re-checked inside the same transaction. Concurrent bookings of one slot
   serialize on the row lock — exactly one wins, the other gets HTTP 409.
2. **Database backstop:** `UNIQUE(appointments.slot_id)` (one appointment per
   slot) and `UNIQUE(time_slots.tenant_id, provider_id, start_at)`. Any race
   that slipped past the lock fails the insert and is mapped to 409, never 500.

Proven by `DoubleBookingTest`: a 2-thread gated-start race asserts exactly one
`BOOKED_OK` + one `Conflict`; plus a direct duplicate-insert test asserting the
unique constraint fires.

### Multi-tenancy

Shared-schema, shared-database with a `tenant_id` column on every table and
tenant-scoped repository queries. `TenantContext` (request-scoped) is set by
`TenantContextFilter` from the JWT claim (authenticated) or header/slug
(public). `TenantIsolationTest` proves tenant B cannot list, update, or delete
tenant A's data, and that the same email can exist in two tenants.

### Auth

JWT access tokens (15 min) + opaque refresh tokens (7 days) stored hashed
(SHA-256) in the DB with rotation. Reuse of a revoked refresh token revokes
the whole token family (theft detection). Login/signup/refresh are
token-bucket rate-limited per IP (10/min). Roles: `TENANT_ADMIN`, `PROVIDER`,
`CUSTOMER`; ownership checks in `BookingService.cancel` (customer owns it, or
the slot's provider, or a tenant admin).

### Availability caching + real-time

`GET /public/availability` is read-through cached (Redis, 60s TTL) and
invalidated on every slot mutation; `AvailabilityCache` falls back to an
in-memory map when Redis is absent. Slot changes also fan out over SSE
(`SseService`), so open booking pages update live without polling.

### What was deliberately simplified

- Mock payments (see note at top) — swap `PaymentProvider` for a Stripe
  SDK implementation for production.
- Single-region, no email/SMS notifications, no-shows, or waitlists.
- SSE (not WebSocket) — one-way updates are all the UI needs.
- Demo seed data is behind `SEED_ENABLED` (default on for the demo).

## Tests

21 integration tests, all passing (`cd backend && mvn test`):

| Suite | Tests | Covers |
|---|---|---|
| `AuthFlowTest` | 5 | signup, login, wrong-password 401, refresh rotation, revoked-reuse 401, logout, duplicate email 409 |
| `RbacTest` | 5 | customer/provider 403 on admin & provider endpoints, anonymous 401 |
| `TenantIsolationTest` | 2 | cross-tenant invisibility + 404s, same email in two tenants |
| `DoubleBookingTest` | 3 | concurrent race (1 win / 1 conflict), sequential 409, DB unique-constraint backstop |
| `PaymentMockTest` | 4 | intent lifecycle, declined card, mock labeling (service + HTTP) |
| `SlotAvailabilityTest` | 1 | generation idempotency, cache read, invalidation on booking |
| `RateLimiterTest` | 1 | token-bucket capacity + per-key isolation |

Tests run on H2 (PostgreSQL mode) with Flyway migrations, no Redis —
exercising the in-memory cache fallback. The app was additionally
smoke-tested end-to-end against real PostgreSQL 16 + Redis 7
(signup → login → availability → book → double-book 409 → SSE event).

## Production notes

- Serve the frontend `dist/` from nginx (see `frontend/Dockerfile`); the Vite
  dev proxy is dev-only.
- Set `JWT_SECRET` (≥32 chars), `DATABASE_URL`, and turn off `SEED_ENABLED`.
- The mock payment provider must be replaced with a real PSP integration.
