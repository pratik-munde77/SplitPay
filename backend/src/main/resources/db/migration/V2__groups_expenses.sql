CREATE TABLE expense_groups (
 id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL DEFAULT '',
 currency VARCHAR(3) NOT NULL DEFAULT 'INR', created_by UUID NOT NULL REFERENCES users(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE group_members (
 group_id UUID NOT NULL REFERENCES expense_groups(id), user_id UUID NOT NULL REFERENCES users(id),
 PRIMARY KEY(group_id,user_id)
);
CREATE INDEX group_members_user ON group_members(user_id);
CREATE TABLE expenses (
 id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES expense_groups(id),
 description VARCHAR(200) NOT NULL, amount NUMERIC(14,2) NOT NULL CHECK(amount>0), currency VARCHAR(3) NOT NULL DEFAULT 'INR',
 payer_id UUID NOT NULL REFERENCES users(id), category VARCHAR(30) NOT NULL, date DATE NOT NULL,
 notes VARCHAR(2000) NOT NULL DEFAULT '', split_type VARCHAR(20) NOT NULL,
 created_by UUID NOT NULL REFERENCES users(id), created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX expenses_group_date ON expenses(group_id,date);
CREATE TABLE expense_splits (
 expense_id UUID NOT NULL REFERENCES expenses(id), user_id UUID NOT NULL REFERENCES users(id),
 amount NUMERIC(14,2) NOT NULL CHECK(amount>=0), weight NUMERIC(20,6) NOT NULL CHECK(weight>=0),
 PRIMARY KEY(expense_id,user_id)
);
CREATE TABLE activity_logs (
 id UUID PRIMARY KEY, group_id UUID NOT NULL REFERENCES expense_groups(id),
 user_id UUID NOT NULL REFERENCES users(id), type VARCHAR(40) NOT NULL,
 description VARCHAR(500) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX activity_group_time ON activity_logs(group_id,created_at);
