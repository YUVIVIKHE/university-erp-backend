# Admin users, invitations and university email

## Pages

- Admin → User Management: `/dashboard/admin/users`.
- Admin → Email / SMTP Settings: `/dashboard/admin/email-settings`.
- User invitation acceptance: `/accept-invitation#token=...`.

Both Admin pages verify the current user via `/api/auth/me`. All management APIs
also enforce ADMIN independently of the UI. Other roles cannot configure SMTP,
create invited users, change their roles/status, or send invitations/test email.

## Server setup

Review and apply `src/main/resources/db/manual/002_user_invitations_and_email.sql`
to the existing shared PostgreSQL database before deployment. It adds two user
columns with backwards-compatible defaults, invitation/settings/delivery tables,
and a case-insensitive email uniqueness index. The transaction fails safely if
existing addresses differ only by case; resolve duplicates with the team first.
There are no record deletions, table renames, or student data copies.

Set `ERP_MAIL_ENCRYPTION_KEY` to a cryptographically random 32-byte key encoded as
Base64. Keep it in server secret management, separate from the database and source
control. Preserve the key across restarts/backups. Losing or changing it prevents
decrypting saved SMTP passwords. Re-enter credentials to rotate the key.

The workspace `start-local.ps1` generates and retains a local key under `.runtime`
when no environment key is supplied. Never commit that file. Restart the backend
after configuring the key. This local convenience is not a production secret store.

## Configure mail

SMTP accounts are now stored as separate profiles. Apply
`src/main/resources/db/manual/003_smtp_accounts.sql` after migration 002.
It preserves the existing profile, adds automatic profile IDs and a partial
unique index enforcing at most one active sender. Review duplicate active profiles
before deploying if the database has been customized.

The Admin page lists ACTIVE/INACTIVE profiles with Add, Edit, Activate and
Deactivate actions. Saving a new active profile deactivates the previous one
without deleting credentials. Deactivating the last active profile stops outbound
email; there is no fallback to inactive credentials. Profiles are shared by this
single-institution installation, not isolated per college.

The standard form only asks for university email, password, SMTP host and
connection (587/STARTTLS or 465/SSL). Custom ports are available on demand.
The university email is used as the SMTP username and default sender; sender name
defaults to University Office. Existing sender names/reply-to settings are retained
when editing. Invitation URL is taken from the Admin's browser origin when saving,
so save from the public HTTPS ERP address when deploying. The full backend SMTP
contract remains available for provider accounts that need a different username
or authorized sender address. The frontend form assumes an email-based username.

New profile endpoints (ADMIN only): GET/POST `/api/admin/email-settings/accounts`,
PUT `/api/admin/email-settings/accounts/{id}`, PATCH
`/api/admin/email-settings/accounts/{id}/status` with `{ "active": true/false }`.
The existing settings GET/PUT endpoints remain compatible and select the active
profile (or latest saved one for editing). Outgoing mail always selects only the
active profile, using the same profile for invitation links and sender credentials.

The backend supports the university SMTP host, port, username, mailbox/app password,
sender identity, optional reply-to, TLS mode, enabled flag, and public portal URL.
Only encrypted SMTP credentials are stored; read APIs never return the password
or ciphertext. Leave password blank to retain it; changing server/username
requires entering the new password. STARTTLS and implicit TLS verify certificates.

The portal URL must be the HTTPS origin reachable by recipients. HTTP is allowed
only for localhost/127.0.0.1 development. A localhost invitation will only work on
the computer running the frontend. Save settings before sending a test email.
SMTP acceptance is not proof of inbox delivery; check spam and provider delivery
reports. No real university email can be tested until valid credentials are saved.

EmailService is the shared outbound service for invitations and future application
mail. EmailTransport sets the configured sender for every message. Messages are
plain text; no password or raw invitation token is stored in delivery/audit logs.

## Account lifecycle

Create Faculty or Accountant accounts without choosing a password. Students must
have approved admissions and use StudentService provisioning. If approval already
created their user/student records, invite that existing user from the list.
This screen cannot create or change Admin accounts, edit its own Admin access,
or convert staff into Students (or vice versa).

Sending an invitation marks the account pending and revokes existing sessions.
The UI asks for confirmation before inviting an existing account. A disabled
account must first be enabled. Delivery failure keeps the pending account and
records a safe error; fix settings and resend after 30 seconds. A resend replaces
the previous token. Links expire after 24 hours and can be used only once.

Invitation acceptance verifies a SHA-256 token hash, locks the account and token,
sets a BCrypt password, consumes the invitation and enables login. Tokens travel
in URL fragments to avoid HTTP access logs. Passwords require 12 characters and
at most 72 UTF-8 bytes. An invitation never accepts a recipient-supplied role.

Disabling a user revokes pending invitations. Changing access/role increments the
token version, invalidating both access and refresh tokens immediately; later
reactivation does not resurrect those tokens. Older tokens remain compatible
until their user receives an access change. Existing role authorities still come
from the shared User entity. Changes and invitation acceptance are audited.

## API contracts

- GET/POST `/api/admin/users`: search/filter/paginate or create and invite.
- PATCH `/api/admin/users/{id}`: fullName, role, active.
- POST `/api/admin/users/{id}/invitation`: invite/resend.
- GET/PUT `/api/admin/email-settings`: masked read or save.
- POST `/api/admin/email-settings/test`: recipient.
- POST `/api/auth/invitations/inspect`: token.
- POST `/api/auth/invitations/accept`: token, password.

Successful create can still report delivery.sent=false: do not create a duplicate
account; fix email configuration and resend. Existing `/api/auth/register` remains
compatible; the new Admin UI uses the invitation API instead of sharing passwords.

## Validation

Backend tests cover Admin access, student provisioning restrictions, invitation
expiry/reuse/resend, session versions, encryption, saved-password retention and
failure handling. Test the sender with the university SMTP provider before rollout.
