# Crochet Studio

## Java / Spring Boot application

The Java edition is in **[crochet-java/](crochet-java/)**. It includes the Spring Boot backend, React frontend, PostgreSQL migrations, Docker Compose, optional Kafka, and setup documentation.

```sh
cd crochet-java
bash setup.sh
docker compose up --build -d
```

Open http://localhost:8088 after the containers become healthy. See **[Java setup and account instructions](crochet-java/README.md)**, **[architecture](crochet-java/docs/ARCHITECTURE.md)**, and **[verification and limitations](crochet-java/TESTING.md)**.

This source commit does not deploy the Java application. Cloud database, durable upload storage, production email, and hosting still need configuration before public use.

## Earlier TypeScript local edition


Start with **[LOCAL-SETUP.md](LOCAL-SETUP.md)**. It includes Mac/IDE instructions, local admin/customer test accounts, SQLite access, uploads, backups, and optional Docker Kafka.

```sh
pnpm install --frozen-lockfile
pnpm local:setup
pnpm dev
```

Shop: http://127.0.0.1:5173

Local account switcher: http://127.0.0.1:5173/__local

Complete application reference: [docs/PROJECT-DOCUMENTATION.md](docs/PROJECT-DOCUMENTATION.md). Where the hosted guide differs, LOCAL-SETUP.md is authoritative for this local edition.
