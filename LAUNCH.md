# Crochet Studio public application

## Current implementation
Public catalogue reads; owner-only admin page; customer account page; platform ChatGPT sign-in; server-authorized catalogue administration, profile addresses, order enquiries, private inspiration images, quote acceptance with amount matching, conversations with polling, request rate limits, manually verified offline payment records, fulfilment state transitions and tracking URLs. Products are database-backed; there are no sample products. Quoted amounts are stored in paise. Customer order access is identity-scoped. ADMIN_EMAIL is a runtime secret configured to the verified owner, never supplied by a customer.

## Public launch limitations
Online payment gateway and courier booking APIs require provider onboarding and are not connected. Recording a payment reference does not charge a customer or automatically verify a transaction. Tracking URLs are supplied by the maker. Inventory is manually maintained and enquiries do not reserve it. Marketplace API synchronisation is not connected. Customers sign in with ChatGPT; Google/email-password identity is not supported by the current Sites workflow.

## Validation
Run node --experimental-strip-types tests/permissions.mjs and tests/api-permissions.mjs, plus tsc --noEmit and the Sites build helper. Permission tests cover anonymous requests, cross-customer order and image access, customer attempts to quote or mark payment, public catalogue access and admin-only draft access. These use mocked platform identity and storage; they do not replace end-to-end browser or load testing. Migration SQL is checked using SQLite.

## Before accepting substantial traffic
Configure payment signature-verified webhooks, idempotent payment ledger and refunds; courier APIs and shipping webhooks; transactional inventory reservation; notifications/outbox; backups, monitoring, upload/orphan cleanup, retention policy and measured load tests. Current catalogue reads are capped at 200 products, order lists support 50-item pagination, and conversations show the latest 200 messages. Domain and branding remain undecided.
