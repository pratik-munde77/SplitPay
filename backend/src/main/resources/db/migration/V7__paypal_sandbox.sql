ALTER TABLE settlements ADD COLUMN checkout_amount NUMERIC(14,2);
ALTER TABLE settlements ADD COLUMN checkout_currency VARCHAR(3);
ALTER TABLE settlements ADD COLUMN checkout_rate NUMERIC(18,6);
