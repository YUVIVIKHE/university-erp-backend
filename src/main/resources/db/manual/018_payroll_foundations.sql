CREATE TABLE IF NOT EXISTS finance_payroll_profiles (
 user_id BIGINT PRIMARY KEY REFERENCES users(id), employee_code VARCHAR(30) UNIQUE NOT NULL,
 department_id BIGINT REFERENCES programs(id), designation VARCHAR(100) NOT NULL,
 joined_on DATE NOT NULL, active BOOLEAN NOT NULL DEFAULT true, version BIGINT NOT NULL,
 updated_by VARCHAR(255) NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS finance_salary_structures (
 id UUID PRIMARY KEY, request_id UUID UNIQUE NOT NULL, request_hash VARCHAR(64) NOT NULL,
 user_id BIGINT NOT NULL REFERENCES finance_payroll_profiles(user_id), effective_from DATE NOT NULL CHECK(extract(day FROM effective_from)=1),
 basic NUMERIC(12,2) NOT NULL CHECK(basic>0), allowances NUMERIC(12,2) NOT NULL CHECK(allowances>=0),
 deductions NUMERIC(12,2) NOT NULL CHECK(deductions>=0), net NUMERIC(12,2) NOT NULL CHECK(net=basic+allowances-deductions AND net>0),
 notes VARCHAR(1000) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN('PENDING','APPROVED','REJECTED','CANCELLED')),
 created_by VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 reviewed_by VARCHAR(255), reviewed_at TIMESTAMPTZ, review_note VARCHAR(2000)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_salary_effective ON finance_salary_structures(user_id,effective_from) WHERE status IN('PENDING','APPROVED');
CREATE INDEX IF NOT EXISTS idx_salary_staff ON finance_salary_structures(user_id,effective_from DESC);
CREATE INDEX IF NOT EXISTS idx_salary_queue ON finance_salary_structures(status,created_at DESC,id);
CREATE TABLE IF NOT EXISTS finance_payroll_snapshots (
 entry_id UUID PRIMARY KEY REFERENCES finance_operating_entries(id), salary_id UUID NOT NULL REFERENCES finance_salary_structures(id),
 employee_code VARCHAR(30) NOT NULL, designation VARCHAR(100) NOT NULL, department_name VARCHAR(120),
 basic NUMERIC(12,2) NOT NULL, allowances NUMERIC(12,2) NOT NULL, deductions NUMERIC(12,2) NOT NULL,
 gross NUMERIC(12,2) NOT NULL, net NUMERIC(12,2) NOT NULL CHECK(gross=basic+allowances AND net=gross-deductions AND net>0)
);
