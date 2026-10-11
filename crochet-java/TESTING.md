# Validation status

## Completed

- Java main source compiled and an executable Spring Boot JAR was built using Java 17 and Maven 3.9.11.
- React/TypeScript type checking passed.
- Vite production build passed.
- Setup shell syntax checked with `bash -n setup.sh`.

## Unit tests

Run `mvn verify` in `backend`. `AccessTests` covers super-admin-only approval, customer denial for catalogue writes, cross-customer order read denial, pending admin login denial, order transitions, password hashing and token generation.

Final unit test result: **6 tests passed, 0 failures, 0 errors, 0 skipped**. The tests use Mockito subclass mocking so they do not require dynamic JVM agent attachment. These are isolated access/business-rule tests, not PostgreSQL integration tests.

## Not executed in this environment

Docker and PostgreSQL were not available here, so the complete Compose stack, Flyway migration against PostgreSQL, live database transactions, SMTP delivery, Kafka delivery and signed-in browser flows have not been verified end to end. No load testing has been performed. Compilation and unit tests do not establish production readiness.

## Local acceptance check

1. Start Docker Compose and wait for `/api/health` to report UP.
2. Sign in as the bootstrapped super admin; confirm `users` has exactly one SUPER_ADMIN in DBeaver.
3. Register a customer, verify through Mailpit, and save/reload a delivery address.
4. Register a staff member. Before verification/approval, confirm login is denied.
5. Verify the staff email; confirm an owner notification arrives in Mailpit.
6. Confirm a customer and an ordinary staff session cannot access `/api/admin-requests` (HTTP 403).
7. Approve the request as super admin. Sign in as staff and add a draft product/photo, then publish it.
8. As a customer, submit an enquiry and private inspiration image. Confirm a second customer cannot read its conversation or image.
9. Exchange messages, quote, accept the current quote, record a real/test payment reference, and advance through making/shipping/delivery. Use only test data locally.
10. Revoke staff access and confirm the existing session loses access immediately.
11. Exercise password recovery; confirm old sessions and used reset links are invalidated.
12. Enable the Kafka profile, create another request, and inspect published events. Stop Kafka temporarily and confirm failed events remain retryable in PostgreSQL.
13. Restart the stack and confirm data and images persist.

Use separate browser profiles/incognito sessions for each identity. Do not mark real payments as verified merely to test the workflow.
