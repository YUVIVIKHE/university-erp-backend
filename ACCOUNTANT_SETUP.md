# Accountant portal

Uses the shared users, JWT authentication, PostgreSQL database, admissions and
fees. No separate student database or default Accountant password is created.

## Setup

1. Configure the environment variables in [README.md](README.md).
2. Review and apply the numbered SQL files in `src/main/resources/db/manual`
   in order. They are manual migrations; SQL initialization is disabled.
   Run with autocommit: some indexes use `CREATE INDEX CONCURRENTLY`.
3. An Admin creates an Accountant through user management. See
   [Admin users and email](ADMIN_USERS_AND_EMAIL.md) for invitations and SMTP.
4. Sign in at `/accountant/login`. The regular login also redirects Accountants.

Existing installations must review their role constraints before applying 001.
Hibernate schema update is for development; coordinate shared database changes
with the team. Preserve signing/encryption keys outside Git.

## Implemented modules

| Page under `/accountant` | Purpose |
| --- | --- |
| `/` | Fee totals, aging, recent charges and upcoming dues |
| `/fees` | Shared admission fee accounts, individual and bulk assignment |
| `/fee-structures` | Draft, publish and archive fee/installment templates |
| `/collections` | Verified manual collections and immutable receipts |
| `/defaulters` | Outstanding/overdue accounts, follow-ups and reminder drafts |
| `/scholarships` | Scholarship/concession applications and document verification |
| `/refunds` | Receipt-based requests and verified refund payouts |
| `/income-expenses` | Non-fee income and operating expenses |
| `/ledger` | Read-only cash-basis journals, account statements and trial balance |
| `/budgets` | Category plans, actual spending, commitments and remaining allocation |
| `/payroll` | Vendor directory/invoices and HR-approved net staff payouts |
| `/approvals` | Institution-funded concession decisions and postings |
| `/approvals/refunds` | Refund approval queue |
| `/approvals/operations` | Income/expense approval queue |
| `/approvals/budgets` | Budget approval queue |
| `/approvals/disbursements` | Staff/vendor payment approval queue |

Reports, Accountant Settings and the
central Audit Logs page remain placeholders. Financial records retain their own
actors, timestamps, reasons and references.

## API groups

All routes below start with `/api/finance` and require ADMIN or ACCOUNTANT.
Accountants cannot access unrelated academic/Admin APIs.

| Prefix | Operations |
| --- | --- |
| `/dashboard` | Read fee aggregates and bounded attention lists |
| `/fees` | Search charges, approved admissions and shared fee accounts; assign |
| `/fee-structures` | Course lookup, template creation and status transitions |
| `/fee-assignments` | Frozen previews and resumable bulk assignment |
| `/collections` | Collect, list payments, select charges and read receipts |
| `/defaulters` | Worklist, follow-up history and manual reminder drafts |
| `/scholarships` | Applications and document review |
| `/approvals/awards` | Pending/history, approve allocations or decline |
| `/refunds` | Receipt capacity, requests, review and payout recording |
| `/operations` | Categories, entries, review, settlement and filtered totals |
| `/ledger` | Accounts, journals, account movements and trial balance (GET only) |
| `/budgets` | Plan creation, list, utilization detail and separate review |
| `/disbursements` | Vendors, existing staff lookup, payment requests and settlements |

Operational lists use 20-row pages. Mutation request UUIDs support safe retries;
reuse the same payload and actor after an uncertain response. Changed retries
conflict. Shared fee rows lock consistently during payments, concessions and
refunds. Bulk assignment uses frozen selections and bounded transactions.

## Financial rules

- Collectible balance = max(original fee − concessions − net paid, 0), excluding
  waived charges. Overdue means due before today; due today is not overdue.
  Financial dates default to `Asia/Kolkata` via `app.finance.time-zone`.
- Fee DTOs expose `concessionAmount`, account summaries `concessions`, and the
  dashboard `totalConcessions` and `totalRefunded`. `amountPaid` is net of paid
  refunds. Stored PAID means settled, including concession-funded settlement.
- Gross charges and original receipts remain unchanged. Refund payouts reduce
  net paid and reopen debt; they do not cancel the original charge. Existing
  waived charges remain waived. Reconcile gross receipt history with paid
  refunds when producing reports.
- A separate finance staff member must review applications, refunds and
  operating entries. The creator may execute an approved manual settlement;
  there is no third-person rule or per-amount approval hierarchy yet.
- Verified institution-funded awards allocate only against their student's
  unpaid fees, capped by the requested amount and current balances. External
  sponsor funding requires a separate reconciliation workflow.
- Pending/approved/paid refunds reserve the original receipt allocation.
  Rejection or withdrawal releases capacity. Legacy payments without a finance
  receipt are not refundable through this workflow.
- Operating entries cover non-fee cash only. Do not re-enter student fees,
  refunds, sponsor fee funding, payroll or vendor invoice payments. Totals count
  SETTLED entries across all matching rows; date filters use document dates.
  These are cash totals, not accrual profit/loss or double-entry journals.
- Collection, refund and operating settlement screens record verified manual
  transactions; they do not transfer money. Reminder drafts do not send mail.
- Final decisions/postings cannot be edited through these APIs. Reversals,
  partial operating settlements, gateway integration, bank reconciliation and
  automatic tax calculations and enforced spending limits remain pending.

## Team coordination

Keep existing `admissions` and `fees` as the source of student fee records.
Admission records do not yet have a normalized student/course enrollment link.
Coordinate shared fee DTO/balance rules, eligibility, document storage, scheme
ownership and finance-role permissions with Admin/Admissions developers.
The current schema has no institution/tenant key; multi-college isolation needs
a shared tenant model before deployment. Future ledger work should consume
immutable source IDs once rather than duplicating financial transactions.

## General Ledger scope

Apply `015_general_ledger.sql` after the earlier migrations. It adds a small
system chart of accounts and read-only views over existing receipt, paid refund
and settled operating records. No source transactions or student balances change.
Each source UUID produces one balanced debit/credit pair automatically; no
posting/synchronization action or duplicate transaction store is introduced.
Cash maps to 1000; other payment methods map to 1010 Bank / digital clearing.
Fee refunds debit contra-income 4090; operating categories map to their seeded
income/expense accounts. Review these initial mappings with the institution's
accounting owner before production. Clearing is not a reconciled bank account.

`GET /api/finance/ledger/accounts`, `/journals`, `/trial-balance` and
`/accounts/{code}` require ADMIN/ACCOUNTANT. Transaction-date filters are inclusive;
lists use 20-row pages. Trial/account opening balances derive only from earlier
recorded movements. DR/CR indicate signed debit/credit balances; account running
balances include earlier pages and rows display newest first. Reads use a
repeatable-read snapshot. Journal search/source filters do not filter the trial
balance or account statement; date filters apply across all views.

This is a cash-basis foundation: unpaid fees, concessions, unreceived sponsor
funding and legacy payments without finance receipts are excluded. It is not
full accrual accounting. Manual journals, imported opening balances, configurable
accounts/mappings, period closing, reversals and bank reconciliation remain
pending. Current account balances must not be presented as complete historical
institution balances until opening balances and legacy records are reconciled.

## Budget scope

Apply `016_budget_management.sql` with autocommit. `/accountant/budgets` creates
institution-wide INR plans using the existing operating expense categories.
Review is under Financial Approvals → Budgets. A different Admin/Accountant
approves/rejects; the creator may withdraw a pending plan. Final decisions and
allocations cannot be edited. Duplicate active name/periods and approved
category/period overlaps are rejected, including concurrent approval attempts.

`GET/POST /api/finance/budgets`, `GET /{uuid}` and `POST /{uuid}/review` expose
20-row pages and utilization without copying expenses. Actual spending uses
SETTLED operating expenses by settlement date; commitments use APPROVED,
unsettled operating expenses by document date. Both date boundaries are inclusive.
Remaining = allocation − actual − committed; negative category values flag
overruns. Settling in another period moves the expense into that period.
Pending expenses, income, student transactions, payroll and vendor invoices are
excluded. Non-approved plans show previews only. This monitor does not reserve
funds or block payments; department cost centers, amendments, carry-forward and
enforced limits remain pending. Budget actions do not change fee or ledger totals.

## Staff and vendor payments

Apply `017_staff_vendor_payments.sql` after 016. It adds vendors, nullable payee
links on the existing operating payment records, unique active invoice/month
keys and ledger mappings. It preserves all earlier transactions. Staff lookup
uses active shared ADMIN/ACCOUNTANT/FACULTY users; students and pending invitees
are excluded. No independent employee or credential database is created.

`/accountant/payroll` has Vendor invoices, Staff net payouts and Vendors tabs.
Vendor names are unique and retained for historical references. Deactivation
blocks new invoices; existing approved obligations remain payable. Vendor name
editing, bank credentials and vendor invitation emails are not included.
Payroll requests specify a current/past month, HR-approved reference and net
amount. One active request per staff/month is allowed. Salary, attendance,
statutory deductions, employer liabilities and payslips are not calculated.

Separate review is under Financial Approvals → Payroll & vendors. Requesters
cannot approve their requests; payroll beneficiaries also cannot review their
own payout, including through the shared operating API. Shared settlement rules
require approval, proof reference, valid date and verified=true. Full manual
payouts only; no bank transfer is initiated. Shared method/reference uniqueness
also prevents reusing an operating/vendor/payroll settlement reference.

All workflows share `finance_operating_entries`; specialized records are excluded
from the ordinary Income & Expenses list/totals. Their category keys are not
available in the ordinary entry form. Existing category budgets exclude these
specialized payments. Settled source IDs appear once in General Ledger as
VENDOR or PAYROLL: debit 5210 Vendor payments or 5200 Staff net payouts, credit
cash/clearing. These cash-basis amounts are not full accrual payroll expenses.

Finance-only endpoints under `/api/finance/disbursements`: `/vendors` GET/POST,
`/vendors/{uuid}/active` PUT, `/staff` GET, and `/{VENDOR|PAYROLL}/payments`
GET/POST. Payment suffixes `/{uuid}`, `/{uuid}/review` and `/{uuid}/settlement`
read details, decide and record payouts. Lists use 20-row pages and full matching
totals. Retry the same UUID/body/actor after uncertain creation or settlement.
Final decisions and payouts are immutable through these APIs.

Validation: 89 backend tests, frontend type/lint checks and live duplicate,
beneficiary, shared-reference, ledger and ordinary operating regression checks.
Mobile vendor request/approval/payout was verified; forms/registers
checked at 1440/768/390px. A 5,000-invoice local fixture with ten reads at five
concurrent had maximum 93ms; this is not a deployment capacity guarantee. Test
data was removed and original balances restored; no screenshots, funds or mail
were generated. Partial payments, payroll batches/calculations, invoice taxes,
purchase-order matching, amendments and reversals remain pending. Coordinate
the staff eligibility, source categories, HR approval references and account
mappings with the team before production; tenant isolation is still required.

## Payroll profiles and salary versions

Apply `018_payroll_foundations.sql` after 017. Under Payroll & Vendor Payments,
open Staff payroll setup to link an existing staff user to a unique employee
code, shared department, designation and joining date. Profile edits use version
checks; pausing a profile blocks new payout requests while preserving history.

Request fixed basic salary, allowances and deductions with a month-start effective
date. A separate reviewer, who is not the beneficiary, must approve it. Approved
versions advance chronologically. Monthly preview selects the applicable approved
version; generation saves its breakdown with the existing payout request. Later
profile or salary changes cannot alter that snapshot. Existing payout approval,
monthly duplicate protection, verified settlement and ledger posting still apply.

Finance-only APIs are under `/api/finance/payroll`: departments, profiles,
salaries and review, monthly preview/generate, and saved snapshots. Lists are
paged. This is fixed net payout preparation: attendance, partial-month proration,
tax/statutory calculations, payroll batches and statutory payslips remain pending.
Use HR-approved manual net payouts for partial joining months.

## Reports & Analytics

`/api/finance/reports?from=YYYY-MM-DD&until=YYYY-MM-DD` is a finance-only,
read-only report over the shared ledger views. Inclusive date ranges are limited
to five years. It returns cash inflow/outflow/net, monthly and source summaries,
and income/expense account breakdowns from one consistent database snapshot.
No new tables or migrations are needed. Unsettled requests are excluded; refunds
reduce income and payroll represents net payouts. This is cash-basis reporting,
not an accrual statement. The Accountant report screen exports the loaded summary
to CSV and supports printing; detailed work stays in the existing registers.

Validation: 95 backend tests passed, frontend type/lint checks, live source-SQL
reconciliation and concurrent reads, responsive pages at 1440/768/390px and CSV
contents. Verification changed no records and generated no screenshots.

Detailed reports use `/api/finance/reports/details` with `section`, `group`,
`from`, `until` and zero-based `page`. Sections: collections (program, current
student year, admission batch, saved fee head or payment method), current
balances, ageing, defaulters, awards, refunds, budgets, payroll and vendors.
Groups are paged at 20 rows; summary totals cover every matching group. CSV
exports explicitly include the displayed page plus the full summary, avoiding
unbounded downloads. Current fee balances are labelled with today's institution
date and do not reconstruct historical balances. Student department mapping is
not present in admissions, so department reports await a shared academic mapping.

Request reports select creation/document dates and show current statuses; this
differs from cash reports, which select settlement dates. Awards report posted
fee reductions separately from requested external assistance. Budget reports
select overlapping approved budgets and show full budget-period allocations,
actuals, current commitments, variance and utilisation without proration. Payroll
and vendor totals separate pending, approved-unpaid and paid requests. Current
student year is derived from current semester, not saved at receipt creation.
Comparisons use the preceding equal-length period and the prior-year date range;
monthly charts omit months with no transactions. These are institutional finance
reports, not statutory statements or reconstructed historical status reports.

Expanded validation reconciled known temporary fees, receipts, awards, refunds,
budget actuals, staff and vendor amounts; invalid filters, page boundaries and
concurrent reads passed. Fixtures were removed and original ledger restored.

## Accountant Settings

Apply `019_accountant_preferences.sql` after 018. `/api/finance/settings` GET/PUT
reads or updates only the authenticated user's report defaults. Preferences are
stored against the shared user ID: current month, calendar year or April–March
financial year, plus a default detailed report. Reports use the saved defaults
and the institution's current date when opened. Financial-year preference is a
date shortcut, not a fiscal-year closing configuration.

Writes use optimistic versions, returning 409 for stale sessions, and record
`FINANCE_PREFERENCES_UPDATED` in the existing audit log. The API exposes account
name/email/role and current currency, time zone and supported payment methods;
no passwords or SMTP secrets. Admin configuration and permissions stay separate.
Restore defaults fills the form; Save persists them. No accounting policies,
payment integrations, receipt sequences or financial records are changed.

Validation: 98 backend tests, frontend type/lint checks, local concurrent saves,
account isolation, audit recording and mobile save/report-default integration.
Temporary test users and their preferences/audit entries are removed afterward.

## Account security and Audit Logs

Settings now offers `POST /api/finance/settings/password`: verify the current
password, require a different 12–72-character password within BCrypt's 72-byte
UTF-8 limit, update the hash under a user lock and increment token version. All
old access and refresh tokens become invalid. The UI confirms the password and
returns to sign-in after success. Credentials are never included in audit data.

Apply `020_finance_activity.sql` after 019. New finance POST/PUT/PATCH/DELETE
requests reaching MVC record actor email, method, path, HTTP status and time.
No request bodies, query strings, passwords or financial notes are recorded.
`GET /api/finance/activity` combines this metadata with existing Accountant login
and finance security/preference events. Date/search/category/outcome filters use
20-row pages, with explicit page-only CSV export. No write/delete endpoint exists.

This starts request history from installation, not a historical before/after
ledger reconstruction or tamper-proof compliance archive. Security-filter
rejections and failed sign-ins are not captured by this MVC recorder. Audit
storage failure logs a server error and does not undo a completed financial
transaction; monitor these errors operationally. Login history uses the current
shared user role. Institution/tenant isolation remains a deployment prerequisite.

Final review: 102 backend tests, all Accountant TypeScript/lint checks, 16 live
financial workflow suites, dashboard/fee checks, password/token revocation and
Audit Logs mobile checks passed. All 21 Accountant routes loaded without console
errors, placeholders or page overflow at 1440/390px. Older client screens now use
server route wrappers, fixing navigation validation behind the authentication
gate. Removed the unused placeholder and redundant layout wrappers. Test images
and PDFs were not retained; temporary financial/security fixtures were removed.

Full production build remains blocked by the existing unrelated Admin route
`/dashboard/admission/[id]` (`useParams` during prerender, CLIENT_HOOK_DYNAMIC).
Coordinate that route's rendering fix with the Admin teammate before deployment.

## University-wide, multi-course and study-year fee plans

Apply `021_fee_structure_scope.sql` after 020. Existing plans/runs remain LEGACY:
their explicit admission-program, semester and optional batch filters are preserved.
New plans choose SELECTED (one to 200 active courses) or UNIVERSITY (no course IDs).
Semester 0 means all eligible semesters. Empty studyYears with includeFinalYear=false
means all study years; otherwise explicit years and final year are combined with OR.
Batch, optional semester, search, program and specific-ID filters further narrow
eligibility. Creating/publishing a plan does not itself create student charges.

Admissions currently have program text rather than a course FK. New course targeting
uses an exact normalised course-name match, otherwise a unique active course-program
match across the whole catalog. Ambiguous/unmapped courses do not qualify for selected
course or final-year rules. They may still qualify for university-wide all-year or
explicit-year rules. Preview reports unresolved course mappings and freezes the target
IDs. Processing rechecks eligibility under admission locks and retains 25-account
transactions, unique structure-line charges, expiry and resume/retry protection.

Study year is ceil(semester/2), using the existing two-semesters-per-year convention.
Final year requires a uniquely matched active course with a positive whole-year
duration in months; course years = durationMonths/12. Configure academic course data
before relying on this rule. This is not a new department/student database. A direct
shared academic course mapping remains preferable to program-name resolution.

Validation: 105 backend tests, frontend type/lint checks, live university/all-year,
multi-course, selected-year, final-year (2/3/4-year course) and third-plus-final union
checks; concurrent duplicates, changed eligibility, existing client compatibility,
responsive forms at 1440/768/390px, and a 5,000-student frozen preview/batch check.
Temporary fixtures are removed; local DEMO financial records are preserved.

## Checks and Git

Backend: `mvn test` and `mvn package`.
Frontend: `npx tsc --noEmit` and ESLint on the Accountant routes/components/libs.
Financial changes should also verify live balances, concurrent retries, separate
review, rollback and receipt preservation. Keep meaningful regression tests and
all required migrations in Git; exclude credentials, databases, build output,
screenshots and local runtime logs.


Fee cohort multi-selection: course, study-year, semester and admission-batch
selectors support All or multiple values. Semester/year choices derive from active
course duration (two semesters per year); admission batches come from approved
admissions. Course catalogs refresh on opening/focus and support search/pagination.
Apply additive manual migration `src/main/resources/db/manual/022_fee_multi_cohorts.sql` before starting this version. Legacy scalar cohort filters remain supported.


Scholarship corrections and award reversals require additive manual migration
`src/main/resources/db/manual/023_scholarship_corrections.sql`. Submitters may edit
SUBMITTED/VERIFIED applications before final financial decision, with version
checks, stored prior values and mandatory correction reasons. Edits reset document
verification. Posted awards use a separately reviewed full reversal; original
approvals/allocations and receipts remain intact. Approved reversals close the
application and restore fee balances. The paginated reversal queue is available
under `/api/finance/award-reversals`; finance roles only. Net award reports exclude
approved reversals. Do not modify historical financial rows directly.


Refund workflow additions require additive manual migration
`src/main/resources/db/manual/024_refund_workflow.sql`. Requests have readable
RF-numbers, source/category/evidence/target metadata and optimistic versions.
`PUT /api/finance/refunds/{id}` corrects requester-owned pending requests and
revalidates receipt reservations; verification is reset. `POST .../{id}/actions`
records VERIFY, RETURN, PROCESSING, FAILED, READY or NOTE, with a mandatory note.
Approval now requires separate-user verification. Approved unpaid refunds may be
returned by another reviewer unless payout is processing. Failed payouts must be
resolved before recording payment. PAID requires the existing verified payout
endpoint; process tracking never changes money. `/api/finance/refunds/summary`
returns search-scoped totals across statuses. Detail includes latest 100 events;
correction/return snapshots remain in finance_refund_events. Existing approved
refunds remain payable; existing paid refunds remain unchanged financially.
Student login ownership mapping, student request screens and approved cancellation
of fee liability require separate integration. STUDENT_OFFLINE only records an
offline student request entered by Accounts and does not grant student API access.


Finance workspace controls require additive migration
`src/main/resources/db/manual/025_finance_workspace.sql`. Pending operating/vendor
requests and budgets support creator-owned corrections with versions and stored
history. Payroll amounts/authorisation snapshots stay locked. Approved unpaid
entries support separate-review RETURN and READY/PROCESSING/FAILED tracking;
only verified settlement changes cash. Settled records allow follow-up notes only.
Vendor contacts have versioned edits; invoice snapshots remain intact.
`/api/finance/workspace/profile` reads/updates only the authenticated user's display
name and finance contact profile; email/role/activation remain Admin-controlled.
Profile department text is not a payroll/academic mapping. The workspace also
provides read-only queue counts, current all-time consistency checks and bounded
per-record history. Ledger journal detail is `/api/finance/ledger/journals/{id}`.
These controls do not add bank reconciliation, statutory payroll, approved-budget
amendments or tenant isolation. Review the local demo guide's completion matrix
before presenting capabilities as production-ready.
