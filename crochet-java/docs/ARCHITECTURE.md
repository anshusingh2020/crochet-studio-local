# Architecture and operating notes

## Stack and boundaries

React/Vite serves the browser UI. Nginx serves its assets and proxies `/api` to Spring Boot. Spring Security establishes identity from an opaque, database-backed session cookie; controllers explicitly authorize every protected operation. JdbcTemplate performs parameterized SQL. Flyway owns schema changes. PostgreSQL holds application records and the outbox. Upload bytes live in a Docker volume, with ownership metadata in PostgreSQL. SMTP delivers verification, recovery and owner review messages. Kafka publication is optional.

The backend uses Java, Spring Boot 4.1.1, Spring Security, Spring JDBC, Spring Mail and Spring Kafka. It intentionally uses JDBC rather than Hibernate so transaction locking, SQL and data ownership are explicit.

## Permissions

| Action | CUSTOMER | ADMIN | SUPER_ADMIN |
| --- | --- | --- | --- |
| View public products | Yes | Yes | Yes |
| Own address, requests and conversations | Yes | Yes | Yes |
| Read all orders and private design uploads | No | Yes | Yes |
| Products, quotes, payment records, fulfilment | No | Yes | Yes |
| Kafka event controls | No | Yes | Yes |
| Approve/reject/revoke administrators | No | No | Yes |
| Create another super admin through an API | No | No | No |

The authenticated user's role always comes from PostgreSQL, never request fields. Customer registration fixes the role to CUSTOMER; admin registration fixes it to ADMIN. Only bootstrap configuration creates SUPER_ADMIN, and PostgreSQL restricts that role to one row.

Account states: `unverified → pending → approved` for admins, or `unverified → approved` for customers. Pending administrators can be rejected; approved administrators can be revoked. Rejected/revoked accounts cannot regain access by registering again. A reinstatement workflow is not implemented.

## Authentication and approval

Passwords use BCrypt cost 12. Opaque 256-bit session tokens are stored only as SHA-256 hashes in the database. Cookies are HttpOnly, SameSite=Lax and eight hours long; set `SECURE_COOKIE=true` under HTTPS in production. Identity is checked against current account status on every request. Reset and revocation delete sessions. Verification/reset tokens expire after one hour, are single-use, and appear in URL fragments rather than request paths.

The email verification screen sets the password again so an earlier registration attempt by another person cannot determine the verified user's password. Password recovery does not approve pending admins or restore revoked users. The owner email is a review link, not an approval token: only a logged-in super admin can mutate approval state. Approval actions are audited.

All browser mutations require an exact `Origin` matching `APP_ORIGIN`. The app does not enable credentialed cross-origin access. Nginx overwrites forwarded IP information; Spring's native forwarding is intended for this trusted proxy deployment. Do not expose the backend directly on a public network. Changing the proxy configuration requires reviewing trusted proxy/IP handling. Authentication uses database rate counters; this is not a comprehensive anti-bot or distributed abuse prevention system. MFA is not implemented.

## Main API map

| Endpoint | Behavior |
| --- | --- |
| `GET /api/auth/me` | Current identity and role flags |
| `POST /api/auth` | Customer register/login, verify/reset/resend/recovery/logout |
| `POST /api/admin-auth` | Staff register/login, same verification/recovery actions |
| `GET/POST /api/admin-requests` | Super-admin-only review/list/approve/reject/revoke |
| `GET/POST /api/profile` | Current user's delivery profile |
| `GET /api/catalogue` | Published products; `all=1` requires admin |
| `POST /api/catalogue` | Admin product add/edit/archive |
| `POST /api/files` | Private design image or admin catalogue image upload |
| `GET /api/files?key=...` | Public published product image, or authorized private image |
| `GET /api/studio` | Current user's orders; admins see all |
| `GET /api/studio?order=...` | Authorized conversation/event history |
| `POST /api/studio` | Create, message, quote, accept, cancel or fulfilment status |
| `GET/POST /api/integrations/kafka` | Admin event status/manual publication |
| `POST /api/integrations/kafka/retry` | Admin retry of dead events |

Order lifecycle: requested → quoted → accepted → paid → making → shipped → delivered. Prepayment cancellation is supported. Paid refunds/cancellation are not implemented. Quote acceptance checks the displayed expected amount. Updates lock the order row, and event writes share the transaction. Customer profile addresses are copied to new orders; editing the profile does not rewrite existing order delivery details.

## Persistence and performance limits

Catalogue responses cap at 200 products. Orders paginate in pages of 50 using a timestamp cursor; identical timestamps at a page boundary can cause omissions, so use a composite cursor before depending on large-scale history pagination. Conversations return the latest 200 messages and 100 events. The browser cart is in memory. Inventory reservations and automatic stock deduction are not implemented. PostgreSQL pool size is 10 by default; actual supported traffic has not been load-tested.

Kafka outbox publishing takes row locks with SKIP LOCKED and sends at most five events per batch. Older unpublished/dead events block later events for the same order. A broker success followed by a database failure can cause re-delivery; consumers must deduplicate by event ID. The local upload volume is not shared between independently deployed hosts; use object storage before horizontally scaling file-serving instances.

## Production deployment

1. Choose a JVM/container host with PostgreSQL and persistent uploads/object storage. Cloudflare Workers/Sites cannot execute the Spring Boot JVM backend directly.
2. Configure a production PostgreSQL account, backups and restore tests. Run Flyway migrations as part of a controlled rollout.
3. Use an HTTPS domain and set `APP_ORIGIN` exactly, plus `SECURE_COOKIE=true`. Keep PostgreSQL, Mailpit and backend administrative ports private.
4. Replace Mailpit with your SMTP provider via `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, and `MAIL_FROM`. Verify your sending domain and provider policies. Real mail has not been tested in this package.
5. Store credentials in the host's secret manager; never commit `.env`. Configure logs/alerts and retention for auth records and personal data. Do not use the shared Mailpit inbox in production.
6. Run the end-to-end checks in TESTING.md, security review, backup restore and load tests before accepting real purchases.
7. Configure payment and courier services separately. Kafka is optional and does not itself send messages to customers.

## Migrating the existing hosted application

This ZIP is a separate Java source project; it is not wired to the current Sites deployment. It starts with a new database and no production records. The hosted source, D1 database and R2 objects have not been deleted or copied.

Before a real cutover, export the current D1 records and R2 files with authorized access, map IDs and money fields to the PostgreSQL schema, validate referential integrity and image ownership, and compare record counts. Hosted ChatGPT identities cannot automatically become password accounts; use a verified account-claim or invitation flow to avoid exposing another customer's order history. Do not infer identity ownership from matching display names. Existing Sites staff password hashes use a different format and are not imported by this code; require secure password setup/reset. Test migration on a copy, freeze writes during cutover, and retain a rollback plan.

The current public site received the earlier TypeScript admin-application feature while this Java version was prepared. Hosted email registration remains unavailable until its separate Resend configuration is supplied. The Java version uses SMTP and can be tested locally through Mailpit without those credentials.
