# Crochet Studio — local development edition

Start with **[LOCAL-SETUP.md](LOCAL-SETUP.md)**. It includes Mac/IDE instructions, local admin/customer test accounts, SQLite access, uploads, backups, and optional Docker Kafka.

```sh
pnpm install --frozen-lockfile
pnpm local:setup
pnpm dev
```

Shop: http://127.0.0.1:5173

Local account switcher: http://127.0.0.1:5173/__local

Complete application reference: [docs/PROJECT-DOCUMENTATION.md](docs/PROJECT-DOCUMENTATION.md). Where the hosted guide differs, LOCAL-SETUP.md is authoritative for this local edition.
