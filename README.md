# University ERP Backend

Java 21 / Spring Boot 3.2.5 REST API with Spring Security, JWT, PostgreSQL,
Spring Data JPA and SMTP mail. Build with Maven 3.9+.

Includes academic/admission/student modules, Admin user invitations and encrypted
SMTP profiles, plus Accountant fee management, collections, concessions, refunds,
approvals and operating income/expenses. See ACCOUNTANT_SETUP.md for scope.

## Configuration

Set these environment variables before starting:

| Variable | Purpose |
| --- | --- |
| `ERP_DB_URL` | PostgreSQL JDBC URL; defaults to local university_erp on port 5432 |
| `ERP_DB_USERNAME` | Database login; required |
| `ERP_DB_PASSWORD` | Database password |
| `ERP_JWT_SECRET` | Required cryptographically random signing secret, at least 32 UTF-8 bytes |
| `ERP_MAIL_ENCRYPTION_KEY` | Base64-encoded random 32-byte key for saved SMTP credentials |
| `ERP_BOOTSTRAP_ADMIN_EMAIL` | Optional initial Admin email for a fresh database |
| `ERP_BOOTSTRAP_ADMIN_PASSWORD` | Optional initial Admin password; remove after provisioning |

No default Admin password is provided. Set both bootstrap variables to provision
an initial administrator. Existing accounts are not changed. Store production
secrets outside source control and preserve the email encryption key across restarts.
The application does not automatically load arbitrary `.env` files; configure
environment variables through your shell, IDE or deployment platform.

## Build and run

```text
mvn test
mvn package
java -jar target/erp-0.0.1-SNAPSHOT.jar
```

The default API port is 8080. Override with `--server.port=8081` if needed.
On local networks with broken IPv6 SMTP routing, launch Java with
`-Djava.net.preferIPv4Stack=true` before `-jar`.

## Existing databases

Review and apply the numbered SQL files in `src/main/resources/db/manual` in
order before deploying to an existing database. They are manual migrations;
Spring does not run them automatically. Hibernate schema update remains enabled
for development. Coordinate shared-database deployments with the Admin team.

See [Accountant setup](ACCOUNTANT_SETUP.md) and
[Admin users and email](ADMIN_USERS_AND_EMAIL.md) for roles, API contracts,
invitations, SMTP profiles and deployment details.

Generated build files and local runtime credentials are excluded from source
control. No local database, mail credentials, signing keys or user passwords
are included in this change.
