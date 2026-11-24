# Stockroom

A shop inventory and expiry management app built with Java 17, MySQL, JDBC, and a responsive web dashboard. Shopkeepers create an account for their store; products, suppliers, and preferences are isolated by store.

## Run locally

Requirements: Java 17+, Maven 3.8+, and MySQL 8+.

1. Create the database by running `database/schema.sql` in MySQL Workbench or the MySQL command line.
2. Copy `.env.example` to `.env` and set `DB_URL`, `DB_USER`, and `DB_PASSWORD` for your MySQL database.
3. Run `run.bat` or `mvn package` followed by `java -jar target/stockroom-1.0.0.jar`.
4. Open `http://localhost:8080` and create a shopkeeper account.

For a complete local setup with Docker Desktop, set the database passwords in `.env` and run `docker compose up --build`. The app is available at `http://localhost:8080`.

The database must be reachable before the Java server starts. Opening `index.html` directly only displays the page; authentication and saved data require the Java server and MySQL.

## Shopkeeper accounts

- Sign up creates one store and its first owner account.
- Sign in uses an email and password. Passwords are stored as PBKDF2 hashes; browser sessions use an HttpOnly cookie.
- Product, supplier, and settings APIs are scoped to the signed-in store.
- Profile and store information can be edited from the dashboard. Sign out ends the server session.
- OCR reads expiry dates from product label images in the browser; the image is not uploaded.

## Deploy to Vercel

The root `Dockerfile` and `vercel.json` configure Vercel's container runtime for the Java HTTP server. Vercel deployment requires a reachable managed MySQL database. Configure these project environment variables in Vercel before the service is used:

- `DB_URL` — JDBC MySQL URL for the managed database
- `DB_USER` — database user
- `DB_PASSWORD` — database password

Use a MySQL provider with network access enabled for Vercel's container runtime. Database credentials should be stored only in Vercel environment variables, never committed to Git.

## API

- `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`
- `PUT /api/auth/profile`, `PUT /api/store`
- `GET` and `POST /api/products`; `GET`, `PATCH`, `PUT`, and `DELETE /api/products/{id}`
- `GET`, `POST /api/suppliers`; `DELETE /api/suppliers/{id}`
- `GET` and `PUT /api/settings`
- `GET /api/health`
