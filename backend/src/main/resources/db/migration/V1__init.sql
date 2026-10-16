-- V1: CareSlot initial schema.
-- Written in portable SQL so it runs on PostgreSQL and on H2 (PostgreSQL mode) for tests.

CREATE TABLE tenants (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE app_users (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_users_tenant_email UNIQUE (tenant_id, email)
);
CREATE INDEX idx_app_users_tenant ON app_users(tenant_id);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE clinic_services (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL REFERENCES tenants(id),
    name             VARCHAR(120) NOT NULL,
    description      TEXT,
    duration_minutes INTEGER NOT NULL,
    price_cents      INTEGER NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_clinic_services_tenant ON clinic_services(tenant_id);

CREATE TABLE providers (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT NOT NULL REFERENCES tenants(id),
    user_id    BIGINT UNIQUE REFERENCES app_users(id),
    name       VARCHAR(120) NOT NULL,
    specialty  VARCHAR(120),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_providers_tenant ON providers(tenant_id);

CREATE TABLE working_hours (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    CONSTRAINT uq_working_hours_provider_day UNIQUE (tenant_id, provider_id, day_of_week)
);
CREATE INDEX idx_working_hours_provider ON working_hours(provider_id);

CREATE TABLE time_slots (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenants(id),
    provider_id BIGINT NOT NULL REFERENCES providers(id) ON DELETE CASCADE,
    service_id  BIGINT REFERENCES clinic_services(id),
    start_at    TIMESTAMP NOT NULL,
    end_at      TIMESTAMP NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT uq_time_slots_provider_start UNIQUE (tenant_id, provider_id, start_at)
);
CREATE INDEX idx_time_slots_tenant_provider ON time_slots(tenant_id, provider_id, start_at);

CREATE TABLE appointments (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenants(id),
    slot_id      BIGINT NOT NULL UNIQUE REFERENCES time_slots(id),
    customer_id  BIGINT NOT NULL REFERENCES app_users(id),
    service_id   BIGINT NOT NULL REFERENCES clinic_services(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    payment_ref  VARCHAR(120),
    notes        VARCHAR(500),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_appointments_tenant ON appointments(tenant_id);
CREATE INDEX idx_appointments_customer ON appointments(customer_id);

CREATE TABLE payment_intents (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenants(id),
    provider_ref  VARCHAR(120) NOT NULL UNIQUE,
    amount_cents  INTEGER NOT NULL,
    currency      VARCHAR(8) NOT NULL DEFAULT 'USD',
    status        VARCHAR(30) NOT NULL DEFAULT 'REQUIRES_CONFIRM',
    appointment_id BIGINT REFERENCES appointments(id),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_intents_tenant ON payment_intents(tenant_id);
