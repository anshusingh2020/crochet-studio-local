# Crochet Studio — complete implementation documentation

Version: public application update, 10 October 2026 (India).

Store: https://crochet-studio-anshu.aglowhawk8.chatgpt.site

Admin: https://crochet-studio-anshu.aglowhawk8.chatgpt.site/admin

Customer profile: https://crochet-studio-anshu.aglowhawk8.chatgpt.site/account

## 1. What has been delivered

A responsive crochet storefront with a public catalogue, authenticated customer requests and an owner-only administration area. A customer can select products, share an inspiration image, discuss a design, receive and accept a quote, and follow progress. The maker can manage products, reply, quote, record an independently verified payment, update fulfilment and add courier tracking.

Order and message events are saved durably in the same database transactions as the changes that generate them. An optional Kafka REST publisher can send them to a configured Kafka gateway. The website's customer tracking history works even when Kafka is not connected.

This application is not a connected payment gateway, courier aggregator or marketplace integration. It does not automatically charge money, verify bank transfers, book pickups or send email/SMS/WhatsApp notifications. Those services need provider accounts and credentials. Customers currently sign in with ChatGPT; Google and email/password sign-in have not been implemented. Traffic capacity has not been established by load testing.

The final brand name and custom domain remain undecided. The live catalogue begins empty so sample products are not presented as real inventory.

## 2. Roles and permissions

| Operation | Anonymous visitor | Signed-in customer | Configured admin |
| --- | --- | --- | --- |
| Browse published products and policies | Yes | Yes | Yes |
| View unpublished products | No | No | Yes |
| Create a request or order enquiry | No | Yes | Yes |
| Save a profile/address | No | Own profile | Own profile |
| View an order/conversation/design image | No | Own orders | All orders |
| Send a message | No | Own orders | All orders |
| Accept a quote | No | Own quote | Only if the order belongs to that same account |
| Cancel an unpaid request | No | Own request | Supported pre-payment statuses |
| Create/edit/publish/archive a product | No | No | Yes |
| Issue a quote | No | No | Yes |
| Record payment / fulfilment / tracking | No | No | Yes |
| Inspect/publish/retry Kafka events | No | No | Yes |

The admin role is determined on the server by matching the authenticated email with the secret runtime variable `ADMIN_EMAIL`. It is configured to the verified Site owner. Customers cannot provide a role, promote themselves or become admin by registering first. UI visibility is not the security boundary: APIs recheck identity and ownership.

Hosting access is separate from application roles. Public hosting permits anonymous browsing. It does not grant catalogue-write or customer-data access. A Site owner/editor's source-editing rights are also distinct from customer operations.

## 3. Authentication

Sites manages `/signin-with-chatgpt`, `/signout-with-chatgpt` and `/callback`. The application does not store customer passwords. Sign-in is initiated by a normal top-level navigation, not a fetch or client router.

Hosted requests receive trusted platform identity headers. `app/chatgpt-auth.ts` extracts the site-specific user ID, email and optional name. User IDs are the database ownership keys; emails are display/contact information. `requireChatGPTUser()` protects `/account` and `/admin`. The admin page additionally checks the configured admin email.

A customer does not need an account to browse. They need a ChatGPT identity to submit requests and view private conversations. An alternative public identity provider would require a supported hosting integration or a separate architecture change; it is not silently added to this deployment.

## 4. Customer walkthrough

1. Open the shop and browse published pieces, category filters and product descriptions.
2. Sign in with ChatGPT.
3. Visit **My orders → Manage my delivery address** and save a name, phone and address. Only new orders take a copy; changing the profile does not rewrite existing orders.
4. Add products to the bag and submit an enquiry, or choose **Made just for you** and enter the design details.
5. Optionally attach a JPG, PNG or WebP inspiration image, maximum 5 MB.
6. Use the order conversation to agree materials, dimensions, price, shipping and timeline.
7. Review and accept the quote. The acceptance request includes the displayed amount; a changed quote cannot silently be accepted at a different amount.
8. Agree payment arrangements with the maker. Online checkout is not connected. Never share a UPI PIN, OTP or card credentials in messages.
9. Follow the status and request history. An open conversation refreshes every 15 seconds. Refresh manually if needed.
10. When the maker adds a courier and tracking URL, use the tracking link. Delivery booking itself is external.

An enquiry does not reserve inventory or take a payment. Customers may cancel requests before they are marked paid. After payment, cancellation/refund arrangements need discussion with the maker.

## 5. Admin walkthrough

1. Sign in with the verified owner account and open `/admin`.
2. Select **Add a product**. Enter a name, description, category, price in rupees, quantity and colour/variation. Upload a real product photo if available.
3. Check **Publish in the public shop** when the product is ready. Unchecked products remain drafts. **Hide** removes a product from the public catalogue without deleting it.
4. Open **Orders, quotes & conversations** to see customer requests.
5. Reply about feasibility, confirm delivery details and enter a quote. The quote is stored in paise.
6. After the customer accepts and you independently verify payment, use **Record verified offline payment** and enter the transaction/reference identifier. This records your verification; it does not contact a bank.
7. Move the order through making, shipped and delivered. Marking shipped requires a courier name and a valid HTTPS tracking URL.
8. Maintain stock manually when confirming orders. The current enquiry flow does not automatically deduct or reserve it.
9. Review Kafka event counts. If a gateway is configured, **Publish pending events** processes a bounded batch. **Retry failed events** resets dead events after you fix the cause.

A different admin account must be explicitly configured through the runtime `ADMIN_EMAIL` setting. Do not replace this with a public “choose admin role” option.

## 6. Order lifecycle

| Current state | Next action/state | Who can perform it |
| --- | --- | --- |
| requested | quoted | Admin issues quote |
| requested | cancelled | Customer owner or admin |
| quoted | quoted | Admin revises active quote |
| quoted | accepted | Customer owner, matching displayed amount |
| quoted | cancelled | Customer owner or admin |
| accepted | paid | Admin records verified payment reference |
| accepted | cancelled | Customer owner or admin before payment |
| paid | making | Admin |
| making | shipped | Admin, with courier and tracking |
| shipped | delivered | Admin |
| delivered / cancelled | No further transition | Terminal in the current implementation |

Paid-order cancellation/refund is not represented by these states yet. A payment provider integration should add a ledger, refund states and auditable approvals before automated refunds are offered.

## 7. Architecture and code map

The browser renders React components. Vinext runs the routes on Cloudflare Workers. Server APIs use Drizzle and the `DB` binding to access Cloudflare D1. Images are stored through the private `BUCKET` R2 binding. SQLite triggers append events to the outbox. The optional publisher uses HTTPS to a Kafka REST Proxy v2-compatible gateway.

| Path | Responsibility |
| --- | --- |
| `app/page.tsx` | Server-rendered published catalogue and optional identity |
| `app/studio.tsx` | Storefront, bag, design form, order conversation and tracking |
| `app/globals.css` | Desktop/mobile visual system |
| `app/admin/page.tsx` | Protected server-side admin gate |
| `app/admin/admin.tsx` | Product management and Kafka operations UI |
| `app/account/page.tsx`, `account.tsx` | Protected delivery profile |
| `app/policies/page.tsx` | Contact, shop arrangements and privacy disclosure |
| `app/chatgpt-auth.ts` | Platform identity and sign-in helpers |
| `app/api/catalogue/route.ts` | Published reads and admin product writes |
| `app/api/profile/route.ts` | Own-profile reads and updates |
| `app/api/studio/route.ts` | Order, quote, message and fulfilment API |
| `app/api/files/route.ts` | Private design uploads and controlled product photo reads |
| `app/api/integrations/kafka/route.ts` | Admin Kafka statistics and bounded dispatch |
| `app/api/integrations/kafka/retry/route.ts` | Admin dead-event retry |
| `lib/server.ts` | Identity context, response helpers, bounded JSON parsing, rate limits |
| `lib/permissions.ts` | Admin/ownership checks and fulfilment transitions |
| `lib/kafka-record.ts` | Event envelope and acknowledgement validation |
| `db/schema.ts`, `db/index.ts` | Schema and D1 connection |
| `drizzle/` | Ordered database migrations and metadata |
| `scripts/kafka-consumer.mjs` | Optional external/local REST consumer example |
| `tests/` | Permission, API-denial and Kafka-format checks |
| `public/robots.txt`, `sitemap.xml` | Search crawling configuration |

## 8. Database and files

D1 is a hosted SQLite database, not a database file delivered inside the source ZIP. Sites provisions it from the `DB` binding declared in `.openai/hosting.json`. Uploaded bytes live in R2, not in D1. Inspect the live database through the Site Settings database viewer.

| Table | Fields and use |
| --- | --- |
| `products` | ID, name, description, category, price in paise, stock, image key, colour, published flag, creation timestamp |
| `profiles` | User ID, name, phone, address lines, city, state, PIN code, update timestamp |
| `orders` | ID, owner user ID/email, title, requirements, status, quote in paise, design key, shipping snapshot, payment reference, carrier, tracking URL, creation timestamp |
| `messages` | ID, order ID, sender user ID/role, text, timestamp |
| `order_events` | Event ID/order ID/type, JSON payload, creation time, pending/published/dead state, attempts, next retry time, lease and failure information |
| `request_limits` | Per-user/action/window counter used for basic request throttling |

Indexes cover customer-order lookup, order messages, published products and pending event dispatch. Foreign keys connect messages and events to orders. Customer profile data is not returned by public endpoints.

Customer inspiration images use a user-scoped key. Catalogue images use the `catalogue/` prefix and become publicly readable only when referenced by a published product. Draft images remain admin-only. File responses include content type and `nosniff`; private images use no-store caching. An already fetched public photo may remain in downstream caches for up to the configured five-minute cache lifetime after a product is hidden.

There are four migrations in this update: the original orders/messages migration, catalogue/profile/order-detail extensions, event outbox schema, and atomic event triggers. Applied migrations must not be rewritten. Generate and append later migrations.

## 9. API contract

Private responses use `Cache-Control: no-store`. Writes require authenticated identity and a same-origin request. JSON requests are limited to 16 KB; uploads are restricted separately. Authentication/permission failures return 401, 403 or an ownership-safe 404. Validation failures use 400; stale status/quote conflicts use 409; rate limits use 429. Unconfigured Kafka publishing returns 503 without losing events.

| Endpoint | Method | Input / behaviour |
| --- | --- | --- |
| `/api/catalogue` | GET | Published products, maximum 200 |
| `/api/catalogue?all=1` | GET | Admin-only products including drafts |
| `/api/catalogue` | POST | Admin product fields, optional ID; `action: archive` hides a product |
| `/api/profile` | GET / POST | Read or update authenticated user's delivery profile |
| `/api/studio` | GET | Own orders or all orders for admin, 50 per page; returned cursor supports older pages |
| `/api/studio?order=ID` | GET | Authorized order, latest 200 messages and 100 events |
| `/api/studio` | POST | `create`, `message`, `quote`, `accept`, `cancel`, or admin `status` |
| `/api/files` | POST | Multipart `file`, private inspiration image |
| `/api/files?catalogue=1` | POST | Admin product image upload |
| `/api/files?key=KEY` | GET | Authorized design/draft image, or published catalogue image |
| `/api/integrations/kafka` | GET / POST | Admin event statistics / publish a bounded batch |
| `/api/integrations/kafka/retry` | POST | Reset dead events for admin-controlled retry |

A shop enquiry sends product IDs and quantities, not a trusted client-calculated price. The server looks up published products and validates availability. The final quote is separately agreed with the maker. A shipping snapshot is taken from the current profile when the enquiry is created.

## 10. Kafka: reliability and configuration

Kafka is a backend event stream, not a customer chat screen or an email service. Customers see database-backed request history. A consumer can later turn events into notifications, analytics or additional fulfilment work.

Database triggers insert an event when an order is created, when its status/amount/payment/tracking changes, and when a message is inserted. The event insert participates in the database transaction: rollback rolls back the event. The publisher does not send emails, addresses, image bytes or message text into Kafka. It sends order IDs, event type, timestamp and minimal state/quote/message metadata.

Configure these server runtime values when a compatible gateway is ready:

| Variable | Meaning |
| --- | --- |
| `ADMIN_EMAIL` | Verified owner/admin email; configured as a secret |
| `KAFKA_REST_URL` | HTTPS base URL for a REST Proxy v2-compatible gateway |
| `KAFKA_TOPIC` | Optional topic; defaults to `crochet.order-events` |
| `KAFKA_REST_USERNAME` | Optional Basic-auth username, secret |
| `KAFKA_REST_PASSWORD` | Optional Basic-auth password, secret |

The publisher posts `application/vnd.kafka.json.v2+json` to `/topics/TOPIC`. This is not automatically compatible with every Kafka vendor's HTTP API or Confluent v3 endpoint. Verify the gateway contract before configuring it. Remote gateways require HTTPS, reject redirects, and must not put credentials inside the URL.

Example event:

```json
{
  "key": "order-id",
  "value": {
    "schemaVersion": 1,
    "eventId": "unique-event-id",
    "orderId": "order-id",
    "type": "order.shipped",
    "occurredAt": "2026-10-09T20:00:00.000Z",
    "data": {"status": "shipped", "amountPaise": 129900}
  }
}
```

Each dispatch processes at most five events and publishes only the earliest unpublished event per order. Records use the order ID as the Kafka key. A lease prevents concurrent workers from claiming the same pending event. Successful per-record acknowledgement is required before marking published. Failures use exponential backoff, with a maximum ten attempts. A dead event blocks later events on the same order until an admin retries it, preserving per-order event progression.

Delivery is **at least once**, not exactly once: a gateway may accept a record and the request may fail before the database is updated. Consumers must deduplicate using `eventId`. The example Node consumer records processed IDs in local SQLite and prints event metadata; it does not send customer notifications. If replacing that handler with an external side effect, use the delivery service's idempotency key or a transactional notification outbox.

The dispatcher is manual through the admin page. No unattended scheduler is configured. Before relying on automated notifications, deploy a secured background dispatcher, monitor oldest-pending/dead counts, and run a real broker integration test. Without a configured gateway, events remain pending and the customer history continues to work.

### Local broker example

Apache Kafka is open-source, but a production broker still needs hosting and operation. This official example starts a local development broker; it is not a free hosted high-availability service.

```sh
docker run --name crochet-kafka -p 127.0.0.1:9092:9092 apache/kafka:4.0.2
```

In another terminal, create a development topic:

```sh
docker exec crochet-kafka /opt/kafka/bin/kafka-topics.sh --create --topic crochet.order-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
```

The website publisher also needs a REST Proxy v2 gateway connected to that broker. The Kafka image alone does not provide it. Localhost on your laptop is not reachable from the hosted website; a deployed secure gateway is required for the hosted publisher. Keep development Kafka ports off the public internet.

Once a gateway is configured in the consumer's environment, run:

```sh
node scripts/kafka-consumer.mjs
```

Use `--once` for one polling iteration. Consumer-specific variables are `KAFKA_CONSUMER_GROUP` and `KAFKA_CONSUMER_STATE`. Do not put real credentials in source control or command history. Configure them through environment/secrets tooling.

References: Apache Kafka 4.0 quickstart, https://kafka.apache.org/40/getting-started/quickstart/ ; Confluent REST Proxy API, https://docs.confluent.io/platform/current/kafka-rest/api.html .

## 11. Local setup on a laptop

Prerequisites: Node.js 22.13 or newer, a compatible pnpm version, and Git for source/version operations. Docker is optional and only needed for local Kafka. Kafka 4.0 also supports a Java 17+ native installation.

Extract the supplied source ZIP, open a terminal in its directory and install from the tracked pnpm lockfile:

```sh
pnpm install --frozen-lockfile
pnpm run build
```

The project contains the Sites starter's portable runtime support. On a fresh laptop, follow the runtime guide in `docs/RUNTIME-GUIDE.md` for profile configuration if needed. Local D1 and R2 storage are separate from production.

After building, apply each pending local migration once, in numbered order. Example:

```sh
node --import ./scripts/sites-env.mjs ./node_modules/wrangler/bin/wrangler.js d1 execute DB --local --config dist/server/wrangler.json --persist-to .wrangler/state --file drizzle/0000_nice_gunslinger.sql
```

Repeat with `0001_goofy_mentor.sql`, `0002_lovely_inhumans.sql` and `0003_order_event_triggers.sql`. Do not replay an applied migration. The command updates only local storage.

Then start the portable development server:

```sh
pnpm run dev
```

Portable loopback development has the starter's mock ChatGPT identity `seedy@sites.test`; production does not. To exercise local admin views, configure a local Worker variable `ADMIN_EMAIL` matching that mock identity, using the runtime's local environment mechanism. Never change the hosted owner identity to a development email. The built Wrangler preview (`pnpm start`) does not simulate sign-in.

## 12. Validation and testing

```sh
node node_modules/typescript/bin/tsc --noEmit
node --experimental-strip-types tests/permissions.mjs
node --experimental-strip-types tests/api-permissions.mjs
node --experimental-strip-types tests/kafka.mjs
pnpm run build
```

The automated tests cover anonymous/customer/admin separation, cross-customer order and image denial, forbidden customer product/quote/payment writes, public catalogue access, allowed status transitions, Kafka envelopes and gateway acknowledgements. API tests mock trusted platform identity and database access; they are not real multi-user browser tests. SQLite checks validate all migrations, quote amount matching, trigger rollback and event content minimisation.

The production build is checked through the Sites build helper. Browser end-to-end testing, real Kafka connectivity and load testing are not completed. A successful build/deployment must not be presented as evidence for a specific requests-per-second capacity.

Recommended next integration tests: two real customer identities cannot read each other's orders; owner can reply and quote; customer accepts the displayed amount; changing a quote before acceptance produces a conflict; public visitor cannot read inspiration uploads; publishing/hiding a product changes public visibility; gateway outage leaves events pending; recovery and duplicate events do not cause duplicate notifications.

## 13. Deployment, migration and rollback

The hosted project identity is declared in `.openai/hosting.json`. Sites source synchronisation pushes the exact checked source commit, builds it and packages a deployable archive. Saving a version retains that commit/build; deployment applies pending database migrations before publishing the Worker. Runtime secret changes require deploying a saved version to apply them.

Keep source and database changes backward-compatible when possible. Re-deploying an older source version does not reverse database migrations. Repair an incident with a forward schema migration when necessary, rather than rewriting applied SQL or assuming a source rollback restores data.

Changing hosting access to public is explicitly authorized for this release. Owner/admin permissions are still enforced by the application. The source repository and runtime secrets are not public shop pages.

## 14. Operations and costs

Start with a small, relevant service stack. Adding every available free service creates more failure points and does not make the store scalable. Cloudflare Workers/D1/R2 provide server execution, structured storage and images here; Sites controls their hosted provisioning. Third-party free-tier limits do not imply this hosting platform is unlimited or free forever. Review actual account usage and billing.

Payment processing, shipping, domains, SMS/WhatsApp and production Kafka hosting can incur charges. Meesho catalogue links can be added after listings exist, but automatic marketplace order sync and external-order shipping are unconfirmed. Direct website orders need an independently configured courier/aggregator integration such as Shiprocket.

Operational checks should include errors, rejected writes, database latency, uploaded bytes, pending Kafka age, dead events, carrier delays, backups and restore drills. Request limits currently provide basic per-user throttling only; they are not comprehensive bot protection or a storage quota.

The catalogue is capped at 200 returned products, orders use 50-item pagination, conversations show the latest 200 messages and tracking shows the latest 100 events. Extend cursor pagination for larger catalogues/conversations. Use full-text search only when measured need justifies it. Upload/orphan cleanup, retention automation and backup scheduling have not been configured.

## 15. Remaining provider-dependent completion

| Requirement | Current state | Needed to finish |
| --- | --- | --- |
| Public storefront and owner/customer roles | Implemented | Real catalogue entered by owner |
| Customer design/request tracking | Implemented | Real multi-user acceptance testing |
| ChatGPT login | Implemented | Customer uses ChatGPT account |
| Google/email-password login | Not implemented | Supported identity integration or hosting change |
| Automated payment verification/refunds | Not connected | Merchant account, test/live keys, signed webhooks and ledger |
| Courier pickup/labels/status webhooks | Not connected | Courier account, pickup address, authenticated API and webhook setup |
| Meesho automatic synchronization | Not connected | Official approved integration and seller credentials |
| Kafka event capture | Implemented | No broker needed for database history |
| Kafka live delivery | Adapter implemented, unconfigured | Secure compatible gateway/broker, dispatcher and integration tests |
| Email/SMS/WhatsApp notifications | Not connected | Delivery provider, templates, credentials and consumer/outbox |
| Final name/domain | Pending user's decision | Brand selection and domain/DNS configuration |
| Proven high-traffic capacity | Not measured | Load tests, monitoring, quotas and upgrade budget |

This distinction is intentional: the delivered source is usable for catalogue administration, custom-order discussion and manually managed fulfilment, while provider-dependent automation remains clearly identified.
