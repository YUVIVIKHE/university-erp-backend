-- Request metadata only. No request bodies, query strings, credentials or notes.
CREATE TABLE IF NOT EXISTS finance_activity_events (
 id BIGSERIAL PRIMARY KEY, actor VARCHAR(255) NOT NULL,
 method VARCHAR(10) NOT NULL, path VARCHAR(300) NOT NULL,
 http_status INTEGER NOT NULL CHECK(http_status BETWEEN 100 AND 599),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_finance_activity_date ON finance_activity_events(created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS idx_finance_activity_actor ON finance_activity_events(actor,created_at DESC,id DESC);
CREATE INDEX IF NOT EXISTS idx_finance_shared_activity ON audit_logs(created_at DESC,id DESC)
 WHERE action IN('USER_LOGIN','FINANCE_PREFERENCES_UPDATED','FINANCE_PASSWORD_CHANGED');
