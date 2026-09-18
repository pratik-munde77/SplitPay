CREATE TABLE personal_expenses (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),merchant VARCHAR(200) NOT NULL,
 description VARCHAR(200) NOT NULL,amount NUMERIC(14,2) NOT NULL CHECK(amount>0),category VARCHAR(30) NOT NULL,
 date DATE NOT NULL,source VARCHAR(20) NOT NULL,payment_method VARCHAR(20) NOT NULL,
 notes VARCHAR(2000) NOT NULL DEFAULT '',receipt_url VARCHAR(2000) NOT NULL DEFAULT '',
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,updated_at TIMESTAMP WITH TIME ZONE NOT NULL,version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX personal_user_date ON personal_expenses(user_id,date);
CREATE TABLE budgets (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),budget_month VARCHAR(7) NOT NULL,
 category VARCHAR(30) NOT NULL,amount NUMERIC(14,2) NOT NULL CHECK(amount>0),UNIQUE(user_id,budget_month,category)
);
CREATE TABLE receipts (
 id UUID PRIMARY KEY,personal_expense_id UUID NOT NULL UNIQUE REFERENCES personal_expenses(id) ON DELETE CASCADE,
 user_id UUID NOT NULL REFERENCES users(id),merchant VARCHAR(200) NOT NULL,amount NUMERIC(14,2) NOT NULL,
 date DATE NOT NULL,created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
