# Run Crochet Studio on your own computer

This is a self-contained local-development copy of the application. Open this folder in your IDE. The frontend, server APIs, SQLite database and uploaded images run locally. Kafka is optional. The hosted production project and its database are not modified by these local commands.

## Quick start on Mac

1. Install Node.js **22.13 or newer** from https://nodejs.org if needed. Check `node --version`.
2. Install the package manager used by the project:

```sh
npm install --global pnpm@11.25.0
```

3. Extract `Crochet-Studio-Local.zip`. In VS Code, IntelliJ or another IDE, use **Open Folder** and select the extracted `crochet-studio-local` directory. Open the IDE terminal in that folder.
4. Run:

```sh
pnpm install --frozen-lockfile
pnpm local:setup
pnpm dev
```

5. Open **http://127.0.0.1:5173** for the store.
6. Open **http://127.0.0.1:5173/__local** to switch between admin, customer, second customer or guest.

No Cloudflare account, ChatGPT login, domain, PostgreSQL or MySQL server is required for this local-development flow. Internet is needed to download dependencies. The existing decorative hero image and Google fonts use external URLs; missing internet affects those visuals, not local data storage.

The first catalogue is empty. Enter as Admin and add products through `/admin`. Use a separate private browser window as a customer, submit a request, then switch back to admin to reply and quote. Customer and second-customer accounts help verify that one customer cannot see the other's orders.

Stop the app with **Ctrl+C**. Run `pnpm dev` to restart it; stored local data survives.

## Local test accounts

| Account | Email | Capability |
| --- | --- | --- |
| Admin | admin@crochet.local | Products, all orders, quotes, fulfilment, Kafka dispatch |
| Customer | customer@crochet.local | Own profile, requests and conversations |
| Second customer | customer2@crochet.local | Separate customer's records |
| Guest | No identity | Public catalogue only |

There are no passwords: the local-only account switcher supplies test identities. It accepts only loopback requests and strips caller-supplied identity headers. It is Vite development middleware and is not part of the built Worker. Use `pnpm dev` for this test login flow; `pnpm start` previews a production build and does not simulate login. Do not bind this development setup to a public network.

The dev configuration sets `ADMIN_EMAIL=admin@crochet.local` on the server. Do not use the production owner email as a local password or share real payment credentials to test accounts.

## Connect DBeaver to the local database

You already have DBeaver and Docker. Start with DBeaver after `pnpm local:setup`:

1. Run `pnpm db:path` in the IDE terminal and copy the full `.sqlite` path printed by the command.
2. Open DBeaver and choose **Database → New Database Connection**.
3. Select **SQLite**, then **Next**.
4. In the database file/path field, browse to the file from step 1. Do not select `metadata.sqlite`.
5. Select **Test Connection**. If prompted, let DBeaver download its SQLite JDBC driver, then finish.
6. Expand the connection's **Tables** node. Open `products`, `orders`, `messages`, `profiles` or `order_events`; use the **Data** tab to inspect rows.

For inspection before installing/running the app, select the included `database/crochet-empty.sqlite` instead. That file is only a blank schema snapshot; it will not show later app changes. For a consistent current snapshot while testing, run `pnpm db:snapshot` and open its printed path in a separate DBeaver connection.

Use SQLite's local file connection: host, port, database username and password are not applicable. Stop the app before making manual edits to the active database in DBeaver. Refresh DBeaver's Data tab after creating products/orders in the app.

## Open and query the database

The local Cloudflare D1 emulator uses a real SQLite file. Its active file is underneath:

```text
.wrangler/state/v3/d1/miniflare-D1DatabaseObject/
```

Find the exact active file:

```sh
pnpm db:path
```

Open the returned `.sqlite` path in your IDE's SQLite database tools, if available, or any SQLite-compatible viewer. Do not choose `metadata.sqlite`; that is emulator metadata. No database username or password is needed. Prefer browsing a snapshot while the application is running, and stop the app before editing the active file in an external viewer.

Run SQL directly from the terminal:

```sh
pnpm db:query "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
pnpm db:query "SELECT id, title, status, amount FROM orders"
pnpm db:query "SELECT id, name, price, stock, published FROM products"
pnpm db:query "SELECT id, kind, delivery_status, attempts FROM order_events"
```

Create a consistent SQLite copy for inspection:

```sh
pnpm db:snapshot
```

This prints a new timestamped file under `.local/`. Snapshots are not the active app database. A blank schema snapshot is also included at **database/crochet-empty.sqlite** so you can inspect the tables immediately, even before installing Node packages. It contains no customer records and is not wired into the running app.

Tables: `products`, `profiles`, `orders`, `messages`, `order_events`, `request_limits`. Emulator/migration bookkeeping tables are also present. Money is stored in paise: `129900` means ₹1,299.

`pnpm local:setup` applies pending migrations and is safe to repeat. After future schema changes, generate a migration with `pnpm db:generate`, then apply pending SQL with `pnpm db:migrate`. Do not manually edit an applied migration.

## Uploaded images and persistence

Uploaded design and catalogue images are stored by the R2 emulator under `.wrangler/state/v3/r2/`. These internal files are emulator-managed; view uploads through the website. The database stores the image key, not image bytes.

To back up the complete local application state, stop the dev server and copy the entire `.wrangler/state` directory to a safe location. A SQLite snapshot by itself does not include uploaded images. To reset local test data, first stop the app and move `.wrangler/state` to a backup location, then run `pnpm local:setup`. This creates a fresh empty database.

The supplied archive excludes production data, production secrets, node_modules and machine-specific runtime state. It includes the schema, migrations and a blank database snapshot.

## Optional: Kafka and REST gateway locally

The app already keeps request history and conversations in SQLite. Start Kafka when you want to test publishing/consuming events.

You already have Docker. Start its engine (Docker Desktop on Mac), confirm `docker compose version` works, then run:

```sh
pnpm kafka:up
cp .env.local.example .env.local
```

Restart `pnpm dev` after creating/changing `.env.local`.

The Compose file includes a single Apache Kafka 4.0.2 broker, a topic-initialization job and a Confluent REST Proxy 8.3.2. It creates the `crochet.order-events` topic. Host ports are bound to loopback only:

| Service | Address |
| --- | --- |
| Kafka broker for host clients | localhost:9092 |
| Kafka REST gateway | http://127.0.0.1:8082 |

Check startup:

```sh
docker compose -f compose.local.yml ps
docker compose -f compose.local.yml logs kafka-rest
```

Create an order/message using a customer account. Enter the Admin page, then click **Publish pending events**. This is a manual bounded dispatcher; repeat to publish later events for the same order. There is no automatic background scheduler.

In a second terminal, consume published event metadata:

```sh
pnpm kafka:consumer
```

Stop the consumer with Ctrl+C. Its local deduplication state is in `.sites-runtime/kafka-consumer.sqlite`. It logs events and does not send emails/SMS/WhatsApp notifications.

To inspect Kafka from its bundled command-line consumer:

```sh
docker compose -f compose.local.yml exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:19092 --topic crochet.order-events --from-beginning
```

Stop Docker services while retaining Kafka data:

```sh
pnpm kafka:down
```

Kafka has a named Docker volume for persistence. Removing that volume intentionally erases the broker's local events. This single-node stack is for local development; production durability and security need a separate deployment design.

The local app permits HTTP Kafka access only in Vite development mode, only with the injected local-dev flag, and only for localhost/127.0.0.1. Production builds continue to require HTTPS. The broker and REST Proxy configurations follow their official documentation:

- https://github.com/apache/kafka/blob/trunk/docker/examples/docker-compose-files/single-node/plaintext/docker-compose.yml
- https://docs.confluent.io/platform/current/installation/docker/config-reference.html

Docker was unavailable in the preparation environment, so the Compose stack has not been executed there. Image availability and platform support must be verified on your laptop when pulling them.

## IDE and project navigation

| Folder | What you edit |
| --- | --- |
| `app/` | Pages, customer UI and API routes |
| `app/admin/` | Admin UI |
| `db/schema.ts` | Database tables |
| `drizzle/` | SQL migrations |
| `lib/` | Permissions, request helpers and Kafka event helpers |
| `public/` | Static assets |
| `scripts/` | Local setup, database tools and Kafka consumer |
| `tests/` | Permission and event checks |
| `docs/PROJECT-DOCUMENTATION.md` | Complete application architecture and API reference |

VS Code tasks are included for database initialization, starting the app and type checking. Other IDEs can run the same commands from their terminals. This codebase uses React/TypeScript and Cloudflare's local runtime; it is not a Spring Boot project.

## Verify your installation

```sh
pnpm exec tsc --noEmit
node tests/local-auth.mjs
node --experimental-strip-types tests/permissions.mjs
node --experimental-strip-types tests/api-permissions.mjs
node --experimental-strip-types tests/kafka.mjs
pnpm build
```

Validation completed during preparation: local migration application, repeated-migration handling, SQL queries, SQLite path/snapshot tools, test-account middleware checks, TypeScript and build. Browser end-to-end testing, a clean Mac dependency installation and the Docker Kafka stack were not run in the preparation environment.

## Common issues

- **`pnpm: command not found`**: install pnpm using the command above, then reopen the terminal.
- **Native package / Node errors**: confirm Node is at least 22.13 and reinstall dependencies on that laptop. Do not copy Linux node_modules onto macOS.
- **`no such table`**: run `pnpm local:setup` from the project root. Do not use the hosted database commands or `--remote`.
- **Admin access denied**: use `/__local` and choose Admin. Ensure you started `pnpm dev` after local setup.
- **Port 5173 in use**: stop the previous process or run `pnpm dev -- --port 5174`, then use port 5174 in all URLs. Keep one hostname (127.0.0.1) per browser session.
- **Kafka gateway unavailable**: check Docker service logs, wait for broker readiness, copy the example environment file, and restart the app.
- **Visuals missing offline**: the current decorative photo/fonts use external URLs. Product data and uploaded images still remain local.

Live payments, automatic courier booking, marketplace synchronization and email/SMS delivery remain unconfigured locally as they are in the hosted version. You can test quoting and manually managed fulfilment with dummy data.
