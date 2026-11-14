# Student Model

## Overview

The Student model stores a student's academic and contact profile in the
`students` table. Login identity and authentication data are stored separately
in `users`; each student profile must reference exactly one user.

The Java entity is
[`Student.java`](src/main/java/com/university/erp/entity/Student.java), and the
core table definition is in [`schema.sql`](src/main/resources/schema.sql).

## Relationship to `User`

- `students.user_id` is a required, unique foreign key to `users.id`.
- A `Student` has a lazy, one-to-one JPA association with `User`.
- Deleting a user cascades to its student row in the SQL schema.
- `User` owns the login email, password hash, full name, role, account status,
  permissions, and invitation state. Those values are not duplicated on
  `Student`.
- The user's role should be `STUDENT`.
- The student's login identifier is the linked user's email; `rollNumber` is
  the academic identifier used by student administration routes.

## Fields

| Java property | Database column | Type | Required | Notes |
| --- | --- | --- | --- | --- |
| `id` | `id` | `Long` / `BIGSERIAL` | Yes | Generated primary key. |
| `user` | `user_id` | `User` / `BIGINT` | Yes | Unique foreign key to `users.id`. |
| `rollNumber` | `roll_number` | `String` / `VARCHAR(50)` | Yes | Unique academic identifier. |
| `program` | `program` | `String` / `VARCHAR(100)` | Yes | Student's academic program. |
| `semester` | `semester` | `Short` / `SMALLINT` | Yes | Current semester; schema check is 1–12. |
| `batchYear` | `batch_year` | `Short` / `SMALLINT` | Yes | Admission/cohort year. |
| `dateOfBirth` | `date_of_birth` | `LocalDate` / `DATE` | No | Date of birth. |
| `phone` | `phone` | `String` / `VARCHAR(20)` | No | Contact number. |
| `address` | `address` | `String` / `TEXT` | No | Contact address. |
| `guardianName` | `guardian_name` | `String` / `VARCHAR(255)` | No | Guardian or parent name. |
| `guardianPhone` | `guardian_phone` | `String` / `VARCHAR(20)` | No | Guardian contact number. |
| `enrollmentDate` | `enrollment_date` | `LocalDate` / `DATE` | Yes | Defaults to the current date on entity creation if unset. |
| `isActive` | `is_active` | `boolean` / `BOOLEAN` | Yes | Defaults to `true`. |
| `createdAt` | `created_at` | `OffsetDateTime` / `TIMESTAMPTZ` | Yes | Set when the entity is inserted. |
| `updatedAt` | `updated_at` | `OffsetDateTime` / `TIMESTAMPTZ` | Yes | Set on insert and updated before each entity update. |

`fullName` and `email` in student API responses are derived from the associated
`User`.

## Constraints and validation

The schema requires a user, roll number, program, semester, batch year,
enrollment date, active flag, and timestamps. Roll numbers and user links are
unique. Optional contact fields may be null.

**Validation mismatch to resolve:** `schema.sql` restricts `semester` to 1–12,
but [`StudentUpdateRequest.java`](src/main/java/com/university/erp/dto/StudentUpdateRequest.java)
currently accepts 1–100. The subject and exam models also use 1–12. Align the
update request limit with the schema before relying on database constraint
errors for validation.

The request validates `batchYear` from 2000 through 2200. The SQL schema stores
it as `SMALLINT` but does not include that range check.

## API representation and update behavior

[`StudentResponse.java`](src/main/java/com/university/erp/dto/StudentResponse.java)
returns the student ID, roll number, linked user's name and email, academic
fields, contact and guardian details, active state, and timestamps. Date values
are serialized as ISO date strings.

[`StudentController.java`](src/main/java/com/university/erp/controller/StudentController.java)
exposes:

| Method and path | Behavior |
| --- | --- |
| `GET /api/students` | Paged search by name, email, roll number, or program; optional program and active filters. |
| `GET /api/students/{rollNumber}` | Fetch a student profile by roll number. |
| `PATCH /api/students/{rollNumber}` | Update the non-null fields supplied in the request. |

For patch updates, blank `program` values are ignored. Blank phone, address,
guardian fields, or date of birth clear those values. `isActive` changes only
when supplied.

The student-facing profile endpoint is `GET /api/student/me`; it resolves the
profile through the authenticated user's ID. Student portal endpoints are
available to the `STUDENT`, `ADMIN`, and `FACULTY` roles. Student administration
routes use the general authenticated API access rules.

## Repository and provisioning

[`StudentRepository.java`](src/main/java/com/university/erp/repository/StudentRepository.java)
supports lookup by roll number and linked user ID, duplicate roll-number
checks, and paged search.

Profiles are created through student import or when an admission is approved.
Admission provisioning creates or finds a `User`, then creates a `Student`
profile if one does not already exist. Bootstrap student creation is optional
and uses `ERP_BOOTSTRAP_STUDENT_EMAIL` and `ERP_BOOTSTRAP_STUDENT_PASSWORD`. To
intentionally replace an existing student's password during one startup, also
set `ERP_BOOTSTRAP_STUDENT_RESET_PASSWORD=true`; unset the bootstrap variables
after that startup to avoid repeating the reset.

## Database setup notes

- [`schema.sql`](src/main/resources/schema.sql) defines the core user,
  permission, audit-log, and student tables; it is not the full ERP schema.
- `spring.sql.init.mode` is set to `never` in
  [`application.yml`](src/main/resources/application.yml), so Spring Boot does
  not automatically execute `schema.sql`.
- Review and apply the numbered SQL migrations in
  [`src/main/resources/db/manual`](src/main/resources/db/manual) as directed by
  the backend README.
- Migration `002_user_invitations_and_email.sql` adds a case-insensitive unique
  index on `lower(email)`. Include the same index in the baseline schema if that
  schema will be used without the migration.



$env:JAVA_HOME = "D:\tools\jdk-25.0.5+8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:ERP_DB_USERNAME = 'erp_user'
$env:ERP_DB_PASSWORD = 'fairbazaar123'
$env:ERP_DB_URL = 'jdbc:postgresql://localhost:5432/university_erp'

$env:APP_BOOTSTRAP_STUDENT_EMAIL = 'student@fairbazaar.in'
$env:APP_BOOTSTRAP_STUDENT_PASSWORD = 'zLN3NmCsbQ3ra1!'
$env:APP_BOOTSTRAP_STUDENT_RESET_PASSWORD = 'true'

# quick check that they are set
$env:ERP_DB_USERNAME; if ($env:ERP_DB_PASSWORD) { "password is set" } else { "password is EMPTY" }

mvn spring-boot:run