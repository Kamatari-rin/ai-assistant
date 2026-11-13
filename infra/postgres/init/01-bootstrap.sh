#!/usr/bin/env bash

set -euo pipefail

psql \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 \
  --set=db_name="$POSTGRES_DB" \
  --set=ai_user="$AI_DB_USER" \
  --set=orders_user="$ORDER_DB_USER" <<'SQL'

BEGIN;

\getenv ai_password AI_DB_PASSWORD
\getenv orders_password ORDER_DB_PASSWORD

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

REVOKE ALL PRIVILEGES ON DATABASE :"db_name" FROM PUBLIC;

CREATE ROLE :"ai_user"
    WITH LOGIN
    PASSWORD :'ai_password'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION;

CREATE ROLE :"orders_user"
    WITH LOGIN
    PASSWORD :'orders_password'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION;

GRANT CONNECT ON DATABASE :"db_name"
    TO :"ai_user", :"orders_user";

GRANT USAGE ON SCHEMA public
    TO :"ai_user", :"orders_user";

CREATE SCHEMA ai AUTHORIZATION :"ai_user";
CREATE SCHEMA orders AUTHORIZATION :"orders_user";

REVOKE ALL ON SCHEMA ai FROM PUBLIC;
REVOKE ALL ON SCHEMA orders FROM PUBLIC;

ALTER ROLE :"ai_user"
    IN DATABASE :"db_name"
    SET search_path TO ai, public;

ALTER ROLE :"orders_user"
    IN DATABASE :"db_name"
    SET search_path TO orders, public;

COMMIT;

SQL
