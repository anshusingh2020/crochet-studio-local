# Crochet Studio — Java edition

A local development version of your crochet storefront, with a Java Spring Boot backend, React/TypeScript UI, PostgreSQL database and Docker Compose. Open this folder in IntelliJ IDEA or VS Code. Start with the steps below.

**Status:** source migration prepared for local review. This ZIP does not replace the existing public Sites website or copy its production data. Docker/PostgreSQL, browser end-to-end and real SMTP/Kafka delivery still need runtime verification. See `TESTING.md` for the checks completed here.

## Start on your Mac

Install and start Docker Desktop. You do not need a separate Java, Node or PostgreSQL installation to run the Docker version.

```bash
cd crochet-java
bash setup.sh
docker compose up --build -d
docker compose logs -f backend
```

The initial build downloads dependencies and container images. Wait until the backend reports that it has started; press Ctrl+C to leave the log view without stopping the containers.

| Service | Address |
| --- | --- |
| Website | http://localhost:8088 |
| Staff and super admin sign in | http://localhost:8088/admin/login |
| Super admin approval page | http://localhost:8088/admin/requests |
| Customer registration/sign in | http://localhost:8088/login |
| Local email inbox (Mailpit) | http://localhost:8025 |
| Backend health | http://localhost:8080/api/health |
| PostgreSQL | localhost:5432 |

Use `localhost` consistently in your browser, not `127.0.0.1`: the backend checks the exact `APP_ORIGIN` for write requests.

### Your super admin account

`setup.sh` asks for your email and creates a private `.env` file with generated database and super admin passwords. The default email is `anshusingh2011111@gmail.com`; change it during setup if that is not your intended owner account.

Open `.env` locally and use `SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD` at `/admin/login`. Keep that file private. The super admin account is provisioned on first startup. Restarting the application does not overwrite its existing password. Use password recovery to change it after initialization. Changing the configured email does not transfer ownership: startup fails if it differs from the existing database owner.

There is exactly one application super admin. PostgreSQL enforces a unique partial index on `role='SUPER_ADMIN'`; public registration cannot assign this role, and the review API only changes accounts whose role is `ADMIN`.

### Test admin approval

1. Open an incognito window and visit `/admin/login` → **Register**.
2. Enter an applicant name, email and password (at least 12 characters, maximum 72 UTF-8 bytes).
3. Open Mailpit at http://localhost:8025 and find the verification email for that applicant.
4. Open its link and confirm the password you want to use. The applicant becomes `pending`.
5. Mailpit receives a separate approval request addressed to your super admin email.
6. In your owner browser, sign in and open `/admin/requests`. Approve or reject the request.
7. The approved applicant can now sign in at `/admin/login`.
8. **Revoke access** ends the staff member's sessions and blocks future sign-ins.

**Local emails are captured by Mailpit, not delivered to Gmail or other real inboxes.** Anyone with access to the local Mailpit interface can read these test emails. Mailpit and the database ports bind only to your computer.

Customers register at `/login`, verify their email and can sign in without your approval. Customers cannot promote themselves or approve staff.

## DBeaver

Create a **PostgreSQL** connection:

| Setting | Value |
| --- | --- |
| Host | localhost |
| Port | 5432 |
| Database | crochet |
| Username | crochet |
| Password | `DB_PASSWORD` from your local `.env` |
| Schema | public |

Click **Test Connection**, then expand `crochet → Schemas → public → Tables`. Refresh a table to view newly committed records. This is your local PostgreSQL database, independent of the hosted Cloudflare D1 database.

Useful read-only queries:

```sql
SELECT id, email, name, role, status, created FROM users ORDER BY created DESC;
SELECT id, email, title, status, amount, created FROM orders ORDER BY created DESC;
SELECT * FROM profiles;
SELECT * FROM admin_audit ORDER BY created DESC;
SELECT id, order_id, kind, delivery_status, attempts FROM order_events ORDER BY created DESC;
```

Prices and quote amounts are stored as integer paise. Password hashes and session/token hashes are security-sensitive; do not share full `users`, `sessions` or `auth_tokens` exports.

## Source map

| Folder/file | Purpose |
| --- | --- |
| `backend/pom.xml` | Spring Boot 4.1.1 Maven project; Java 17 minimum |
| `backend/src/main/java/com/crochet/studio/` | Java API controllers, security, mail and Kafka outbox |
| `backend/src/main/resources/db/migration/V1__store.sql` | PostgreSQL schema, applied by Flyway |
| `backend/src/main/resources/application.yml` | Runtime settings, referencing environment variables |
| `backend/src/test/java/com/crochet/studio/AccessTests.java` | Role, ownership, login and state transition tests |
| `frontend/src/` | React storefront, account, admin, login and approval screens |
| `frontend/package-lock.json` | Frontend dependency lock |
| `compose.yml` | Local PostgreSQL, backend, frontend, Mailpit and optional Kafka |
| `setup.sh` | Generates private initial credentials |
| `docs/ARCHITECTURE.md` | Permissions, API map, deployment and migration notes |
| `TESTING.md` | Validation evidence and remaining runtime checks |

The Docker backend uses Java 21. The source targets Java 17 so it can also compile on Java 17 or newer. The frontend requires Node 22.12+ when run outside Docker.

## Work in your IDE

Open `backend/pom.xml` as a Maven project. Install Java 17+ (Java 21 recommended for consistency with Docker) and Maven 3.9+ locally if you want to run/debug without containers.

Start just the infrastructure:

```bash
docker compose up -d db mailpit
```

Set these environment variables in your IDE's run configuration using the values from `.env`:

- `DB_PASSWORD`, `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`
- `DB_URL=jdbc:postgresql://localhost:5432/crochet`
- `SMTP_HOST=localhost`, `SMTP_PORT=1025`
- `APP_ORIGIN=http://localhost:5173`
- `SECURE_COOKIE=false`

Run `StudioApplication.main()` or `mvn spring-boot:run` in `backend`. Stop the Docker backend first if it is already using port 8080.

For the UI:

```bash
cd frontend
npm ci
npm run dev
```

Vite proxies `/api` to localhost:8080. Open http://localhost:5173. For the Docker frontend, restore `APP_ORIGIN=http://localhost:8088`.

## Optional Kafka

Change `KAFKA_ENABLED=true` in `.env`, then run:

```bash
docker compose --profile kafka up --build -d
```

Order and message events are inserted into `order_events` in the same database transaction as the corresponding business change. Every five seconds a publisher sends pending events to topic `crochet.order-events`, keyed by order ID. Retries use backoff; an event becomes `dead` after ten failed attempts. The admin dashboard can retry dead events.

Delivery is at least once. Consumers must deduplicate by `eventId`. Kafka records contain event metadata, not customer addresses or message text. This package does not implement a customer email/SMS/WhatsApp event consumer. In-site conversations remain the customer communication channel.

The single-node Docker broker is for local development, not a highly available production deployment. Inspect records using:

```bash
docker compose --profile kafka exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic crochet.order-events --from-beginning
```

For a backend running directly in the IDE, set `KAFKA_SERVERS=localhost:9094`.

## Stop, restart and preserve data

```bash
docker compose down
docker compose up -d
```

Named volumes preserve PostgreSQL records and uploaded images. Do not use `docker compose down -v` unless you intend to erase local data. Keep `.env` for the same database credentials. To back up the database:

```bash
docker compose exec -T db pg_dump -U crochet -d crochet > crochet-backup.sql
```

Back up the uploads volume separately. A database-only backup does not contain product/design image bytes.

## What is not connected

Online payment gateway/verification/refunds, Shiprocket/courier bookings, pickup addresses, Meesho synchronization and customer email/SMS/WhatsApp notifications are not integrated. Admins can record independently verified offline payments and add carrier/tracking details. Catalogue orders are enquiries; stock is manually managed and not reserved at checkout. No real products or customer records are bundled.

The Java application is not yet publicly deployed. The existing Sites URL still runs the TypeScript/Cloudflare application. Public Java hosting, a domain, HTTPS, production SMTP and production database/file backups need separate configuration. Free-tier availability and costs depend on the selected provider; unlimited free capacity is not promised.
