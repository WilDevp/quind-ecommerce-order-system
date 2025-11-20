-- ============================================
-- Payment Service - Schema Creation
-- ============================================
--
-- Este script crea las tablas para Payment Service:
-- - payments: Agregado raíz del bounded context Payments
-- - processed_events: Tabla de deduplicación (igual que Order Service)
--
-- Decisiones de diseño:
-- - UNIQUE constraint en order_id (one-to-one relationship con Order)
-- - gateway_response como TEXT para guardar JSON completo del gateway
-- - Índices para lookups por order_id y gateway_payment_id
--
-- Orden de ejecución: Después de 02-create-order-tables.sql
-- ============================================

-- ============================================
-- Tabla: payments
-- ============================================
-- Agregado raíz del bounded context Payments.
-- Representa un pago procesado (o en proceso) para una orden.

CREATE TABLE IF NOT EXISTS payments (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),

    -- Foreign Key lógico a Order
    -- UNIQUE porque una orden solo puede tener un pago
    -- Si el primer pago falla, el cliente debe crear una NUEVA orden
    order_id UUID NOT NULL UNIQUE,

    -- Método de pago
    -- Valores posibles: CREDIT_CARD, DEBIT_CARD, PSE, BANK_TRANSFER, PAYPAL, etc
    payment_method VARCHAR(50) NOT NULL,

    -- Montos monetarios
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',

    -- Estado del pago
    -- Valores posibles: PENDING, PROCESSING, COMPLETED, FAILED, REFUNDED
    status VARCHAR(50) NOT NULL,

    -- Información del gateway externo (Stripe, PayPal, PSE)
    -- gateway_payment_id: ID del pago en el sistema externo (para refunds, reconciliación)
    -- gateway_response: Respuesta completa del gateway en formato JSON
    -- JSONB allows efficient querying of JSON fields (e.g., gateway_response->>'status')
    -- Plus: Schema validation, indexing, and better compression than TEXT
    gateway_payment_id VARCHAR(255),
    gateway_response JSONB,

    -- Razón de fallo (si status = FAILED)
    failure_reason TEXT,

    -- Optimistic Locking
    version INTEGER NOT NULL DEFAULT 0,

    -- Auditoría
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_currency_valid CHECK (LENGTH(currency) = 3),
    CONSTRAINT chk_status_valid CHECK (
        status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REFUNDED')
    ),
    CONSTRAINT chk_failed_has_reason CHECK (
        (status != 'FAILED') OR (status = 'FAILED' AND failure_reason IS NOT NULL)
    )
);

-- Índices para Payments

-- UNIQUE index en order_id (ya lo crea el UNIQUE constraint, pero lo explicito)
-- Garantiza one-to-one relationship: una orden = un pago
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_order_id ON payments(order_id);

-- Búsqueda por estado (métricas: cuántos pagos PENDING, FAILED, etc)
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);

-- Búsqueda por gateway_payment_id (para reconciliación con gateway externo)
-- Ejemplo: Stripe webhook dice "payment_id pi_123 fue completado" → buscamos en nuestra DB
CREATE INDEX IF NOT EXISTS idx_payments_gateway_payment_id ON payments(gateway_payment_id);

-- Búsqueda por método de pago (análisis: popularidad de CREDIT_CARD vs PSE)
CREATE INDEX IF NOT EXISTS idx_payments_payment_method ON payments(payment_method);

-- Búsqueda por fecha de creación
CREATE INDEX IF NOT EXISTS idx_payments_created_at ON payments(created_at DESC);

-- Índice GIN para queries en gateway_response JSON
-- GIN (Generalized Inverted Index) permite queries eficientes en campos JSONB
-- Ejemplos de queries que usa este índice:
--   WHERE gateway_response @> '{"status": "succeeded"}'
--   WHERE gateway_response->>'currency' = 'USD'
--   WHERE gateway_response ? 'error_code'
-- Costo: GIN índices son más grandes que B-tree, pero muy rápidos para JSON
CREATE INDEX IF NOT EXISTS idx_payments_gateway_response_gin ON payments USING GIN (gateway_response);

-- Comentarios en la tabla
COMMENT ON TABLE payments IS 'Payment aggregate root - One payment per order';
COMMENT ON COLUMN payments.order_id IS 'Logical FK to Order - UNIQUE constraint enforces one-to-one';
COMMENT ON COLUMN payments.gateway_payment_id IS 'External gateway payment ID (Stripe, PayPal)';
COMMENT ON COLUMN payments.gateway_response IS 'Complete JSONB response from gateway - Queryable with @>, ->, ->> operators';
COMMENT ON COLUMN payments.failure_reason IS 'Reason for payment failure (if status=FAILED)';

-- ============================================
-- Tabla: processed_events (Payment Service)
-- ============================================
-- Tabla de deduplicación para Payment Service.
-- Estructura idéntica a la del Order Service, pero datos independientes.
--
-- Por qué no compartir la tabla entre servicios?
-- - Cada servicio posee sus datos (bounded context)
-- - Evita acoplamiento entre servicios
-- - Permite escalar/deployar servicios independientemente

CREATE TABLE IF NOT EXISTS payment_processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    service_version VARCHAR(20) NOT NULL
);

-- Índices para Payment Processed Events
CREATE INDEX IF NOT EXISTS idx_payment_processed_events_aggregate_id ON payment_processed_events(aggregate_id);
CREATE INDEX IF NOT EXISTS idx_payment_processed_events_type ON payment_processed_events(event_type);
CREATE INDEX IF NOT EXISTS idx_payment_processed_events_processed_at ON payment_processed_events(processed_at DESC);

COMMENT ON TABLE payment_processed_events IS 'Event deduplication for Payment Service';
COMMENT ON COLUMN payment_processed_events.event_id IS 'Event UUID - Primary Key ensures uniqueness';

-- ============================================
-- Triggers para payments
-- ============================================

-- Actualizar updated_at automáticamente
-- Reutilizamos la función update_updated_at_column() creada en 02-create-order-tables.sql
CREATE TRIGGER update_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Incrementar version en cada UPDATE (Optimistic Locking)
-- Reutilizamos la función increment_version_column() creada en 02-create-order-tables.sql
CREATE TRIGGER increment_payments_version
    BEFORE UPDATE ON payments
    FOR EACH ROW
    EXECUTE FUNCTION increment_version_column();

-- ============================================
-- Vistas útiles (opcional)
-- ============================================
-- Estas vistas facilitan queries comunes. No son necesarias, pero útiles para análisis.

-- Vista: Pagos fallidos con razón
CREATE OR REPLACE VIEW failed_payments AS
SELECT
    p.id,
    p.order_id,
    p.amount,
    p.currency,
    p.payment_method,
    p.failure_reason,
    p.created_at
FROM payments p
WHERE p.status = 'FAILED'
ORDER BY p.created_at DESC;

COMMENT ON VIEW failed_payments IS 'View of failed payments for analysis';

-- Vista: Pagos completados en las últimas 24 horas
CREATE OR REPLACE VIEW recent_completed_payments AS
SELECT
    p.id,
    p.order_id,
    p.amount,
    p.currency,
    p.payment_method,
    p.gateway_payment_id,
    p.created_at
FROM payments p
WHERE p.status = 'COMPLETED'
  AND p.created_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'
ORDER BY p.created_at DESC;

COMMENT ON VIEW recent_completed_payments IS 'Payments completed in last 24 hours';

-- Logging
DO $$
BEGIN
    RAISE NOTICE '✓ Payment Service tables created successfully';
    RAISE NOTICE '  - payments (with unique order_id constraint)';
    RAISE NOTICE '  - payment_processed_events (idempotency)';
    RAISE NOTICE '  - Views: failed_payments, recent_completed_payments';
END
$$;
