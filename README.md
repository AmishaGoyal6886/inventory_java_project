# Stockroom

Stockroom is a Java shop inventory and expiry management application. The interface is written with Java and GWT widgets, then compiled by Maven into browser assets. The interface contains no authored HTML, CSS, or JavaScript files. The backend is a Java 17 HTTP service using JDBC and MySQL-compatible storage.

## Features

- Separate shopkeeper sign-up, sign-in, and store data
- Inventory management with search, category, price, quantity, and expiry date
- Stock adjustments and low-stock alerts
- Expiry tracker and inventory reports
- Sales recording that updates stock and totals store revenue
- Supplier records, store details, profile, and preferences
- Password hashing and server-side sessions in secure, HTTP-only cookies

## Run locally

Requirements: Java 17+, Maven 3.8+, and MySQL 8+ (or a compatible service).

1. Run `database/schema.sql` in MySQL.
2. Copy `.env.example` to `.env`, then set `DB_URL`, `DB_USER`, and `DB_PASSWORD`.
3. Run `run.bat`, or run `mvn package` followed by `java -jar target/stockroom-1.0.0.jar`.
4. Open `http://localhost:8080` and create a shopkeeper account.

For a local Docker setup, configure `.env` and run `docker compose up --build`.

The database must be reachable before the Java server starts. The app is served by the Java process; opening an HTML file directly will not run it.

## Deploy to Vercel

The `Dockerfile` and `vercel.json` configure Vercel's container runtime. Set these environment variables on the Vercel project:

- `DB_URL` — JDBC MySQL URL for the managed database, including TLS settings required by your provider
- `DB_USER` — database username
- `DB_PASSWORD` — database password

Never commit database credentials. Database sessions and store data are kept in MySQL so the service can run without sticky in-memory sessions.

## API

- `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`
- `PUT /api/auth/profile`, `PUT /api/store`
- `GET` and `POST /api/products`; `GET`, `PATCH`, `PUT`, and `DELETE /api/products/{id}`
- `GET`, `POST /api/suppliers`; `DELETE /api/suppliers/{id}`
- `GET` and `POST /api/sales`
- `GET` and `PUT /api/settings`
- `GET /api/health`
