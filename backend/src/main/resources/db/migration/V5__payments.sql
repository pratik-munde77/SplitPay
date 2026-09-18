CREATE TABLE payments (
 id UUID PRIMARY KEY,settlement_id UUID NOT NULL REFERENCES settlements(id),
 provider_order_id VARCHAR(100) NOT NULL,provider_payment_id VARCHAR(100) NOT NULL UNIQUE,
 status VARCHAR(30) NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE payment_webhooks (
 event_id VARCHAR(100) PRIMARY KEY,created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
