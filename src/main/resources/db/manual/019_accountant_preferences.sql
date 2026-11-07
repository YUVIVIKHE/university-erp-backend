-- Personal display/report defaults only; no institution-wide financial rules.
CREATE TABLE IF NOT EXISTS finance_user_preferences (
 user_id BIGINT PRIMARY KEY REFERENCES users(id),
 report_period VARCHAR(20) NOT NULL DEFAULT 'YEAR' CHECK(report_period IN('MONTH','YEAR','FINANCIAL_YEAR')),
 report_section VARCHAR(20) NOT NULL DEFAULT 'collections' CHECK(report_section IN('collections','balances','ageing','defaulters','awards','refunds','budgets','payroll','vendors')),
 version BIGINT NOT NULL DEFAULT 1 CHECK(version>0),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
