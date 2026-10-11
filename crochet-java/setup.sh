#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ -e .env ]; then echo '.env already exists; leaving your settings unchanged.'; exit 0; fi
umask 077
read -r -p 'Super admin email [anshusingh2011111@gmail.com]: ' email
email=${email:-anshusingh2011111@gmail.com}
if [[ ! "$email" =~ ^[a-zA-Z0-9._+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then echo 'Enter a valid email.'; exit 1; fi
password=$(openssl rand -hex 16)
db_password=$(openssl rand -hex 24)
cat > .env <<ENV
DB_PASSWORD=$db_password
SUPER_ADMIN_EMAIL=$email
SUPER_ADMIN_PASSWORD=$password
APP_ORIGIN=http://localhost:8088
SECURE_COOKIE=false
KAFKA_ENABLED=false
ENV
printf 'Created .env with generated credentials. Your initial super admin password is stored there.\nRun: docker compose up --build -d\n'
