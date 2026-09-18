CREATE TABLE notifications (
 id UUID PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),group_id UUID REFERENCES expense_groups(id) ON DELETE SET NULL,
 type VARCHAR(40) NOT NULL,message VARCHAR(500) NOT NULL,alert_key VARCHAR(200) UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,read_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX notifications_user_time ON notifications(user_id,created_at);
CREATE TABLE device_tokens (
 id VARCHAR(64) PRIMARY KEY,user_id UUID NOT NULL REFERENCES users(id),token VARCHAR(2048) NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX device_tokens_user ON device_tokens(user_id);
