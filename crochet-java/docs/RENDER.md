# Render + Supabase deployment

The repository-root `render.yaml` deploys one free Docker web service. The multi-stage image bundles React assets into the Spring Boot JAR. The existing local Docker Compose workflow remains supported. Render service creation alone is not proof of a working deployment.

## Private configuration

Open the Render Blueprint for this GitHub repository, review the free plan, and fill the secrets directly in Render. Do not commit these values.

- `DB_URL`: Supabase Connect > Session pooler > JDBC. Use `jdbc:postgresql://<actual-pooler-host>:5432/postgres?sslmode=require`, without embedding user/password. Copy the actual host from the project dashboard.
- `DB_USER`: the session pooler username shown by Supabase, normally `postgres.<project-ref>`.
- `DB_PASSWORD`: saved database password.
- `SUPER_ADMIN_PASSWORD`: unique password of at least 16 characters. The existing owner password is not reset by later env changes.
- `APP_ORIGIN`: exact HTTPS service URL, without trailing slash. After Render assigns the URL, set this before testing registration and login.
- `SUPABASE_SERVICE_ROLE_KEY`: server-only legacy service_role JWT from Supabase API settings, never anon/publishable. Storage uses a private bucket and backend ownership checks; never expose this key in browser code.
- `RESEND_API_KEY`: sending key from Resend.
- `MAIL_FROM`: sender at a domain verified in Resend. A testing sender cannot deliver to arbitrary customers.

Supabase project: `oeyehuezqluqxjptljht` (Seoul). Render uses Singapore. `DB_SCHEMA=crochet` keeps application tables out of Supabase's default public Data API. Do not expose the crochet schema through the Data API. The connector can inspect it through SQL; Java owns authorization. Flyway creates application tables on successful startup.

Create private Storage bucket `crochet-images` with 5 MB size limit and JPEG, PNG, WebP MIME types. No anonymous storage policies are needed: only the backend accesses Storage. Local mode continues using the uploads volume. Existing local image bytes are not automatically migrated.

## Verification after deployment

Confirm Render reports a live deployment, `/api/health` returns 200, and the root page loads. Test owner login, customer registration and email verification, staff approval, an uploaded private design, another customer's denied access, and catalogue images. Verify a saved record and image remain after restarting the service.

Kafka remains disabled until an external broker is configured. Payment/courier fulfillment remains manual; automatic payment processing and Shiprocket booking are not implemented. The free service sleeps when idle and is not an always-on/high-load deployment. Production secrets and end-to-end cloud tests are required before accepting real orders.
