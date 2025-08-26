-- Схема БД для orders/idempotency/outbox
CREATE TABLE IF NOT EXISTS orders (
                                      id UUID PRIMARY KEY,
                                      customer_id UUID NOT NULL,
                                      status TEXT NOT NULL CHECK (status IN ('PENDING','CONFIRMED','CANCELED')),
    total_amount_cents BIGINT NOT NULL CHECK (total_amount_cents >= 0),
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );


CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_customer_created ON orders(customer_id, created_at DESC);


CREATE TABLE IF NOT EXISTS order_items (
                                           order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku TEXT NOT NULL,
    qty INT NOT NULL CHECK (qty > 0),
    price_cents BIGINT NOT NULL CHECK (price_cents >= 0),
    PRIMARY KEY (order_id, sku)
    );


CREATE TABLE IF NOT EXISTS idempotency_keys (
                                                id BIGSERIAL PRIMARY KEY,
                                                idempotency_key TEXT NOT NULL,
                                                path TEXT NOT NULL,
                                                request_hash TEXT NOT NULL,
                                                response_body TEXT,
                                                status_code INT,
                                                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (idempotency_key, path)
    );


CREATE TABLE IF NOT EXISTS outbox (
                                      id BIGSERIAL PRIMARY KEY,
                                      aggregate_type TEXT NOT NULL,
                                      aggregate_id UUID NOT NULL,
                                      event_type TEXT NOT NULL,
                                      payload TEXT NOT NULL,
                                      status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
    retained_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    available_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );
CREATE INDEX IF NOT EXISTS idx_outbox_pending ON outbox(status, available_at);


CREATE TABLE IF NOT EXISTS processed_events (
                                                event_id TEXT PRIMARY KEY,
                                                processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );