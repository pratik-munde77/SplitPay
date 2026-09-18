CREATE TABLE settlements (
 id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES expense_groups(id),
 payer_user_id UUID NOT NULL REFERENCES users(id), receiver_user_id UUID NOT NULL REFERENCES users(id),
 amount NUMERIC(14,2) NOT NULL CHECK(amount>0), currency VARCHAR(3) NOT NULL DEFAULT 'INR',
 method VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, provider VARCHAR(20) NOT NULL,
 provider_order_id VARCHAR(100) UNIQUE, provider_payment_id VARCHAR(100) UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, completed_at TIMESTAMP WITH TIME ZONE, version BIGINT NOT NULL DEFAULT 0,
 CHECK(payer_user_id<>receiver_user_id)
);
CREATE INDEX settlements_group_time ON settlements(group_id,created_at);
