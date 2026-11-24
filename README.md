# Stockroom

Shop inventory and expiry management website with a Java 17 HTTP server, MySQL persistence through JDBC, and a responsive browser interface.

## Requirements

- Java 17 or later
- Apache Maven 3.8+
- MySQL 8.0+

## Run on Windows

For a quick browser preview, double-click `open-preview.bat`. You can also open `index.html` directly.

1. Create the MySQL database and tables by running `database/schema.sql` in MySQL Workbench or with the MySQL command line.
2. Copy `.env.example` to `.env` and enter the MySQL username and password.
3. Install Maven, then run `run.bat` from this folder. Maven downloads the JDBC driver and JSON library, builds the project, and starts the server. `run.bat` opens the browser when the server starts.

If Maven or `.env` is missing, `run.bat` opens the browser preview instead of exiting. The preview uses browser-local demo data; database features require the Java server.

The Java server also creates missing tables at startup. The database itself must exist before startup. Credentials are read from `.env` by `run.bat`; do not commit your real `.env` file.

## Docker deployment

The complete Java and MySQL app is containerized. Install Docker Desktop, copy `.env.example` to `.env`, replace both password values with long random secrets, then run `docker compose up --build -d`. The site will be available at `http://localhost:8080`. Docker Compose keeps MySQL data in a persistent volume. For public hosting, deploy this Compose stack to a provider that supports persistent MySQL volumes, or use a managed MySQL service and deploy the Java container with its database connection environment variables. Never publish the example passwords.

## API

- `GET /api/health` — server and database status
- `GET /api/products` — list products
- `POST /api/products` — create a product
- `GET /api/products/{id}` — read one product
- `PUT` or `PATCH /api/products/{id}` — update a product
- `DELETE /api/products/{id}` — delete a product
- `GET /api/suppliers`, `POST /api/suppliers`, `DELETE /api/suppliers/{id}`
- `GET /api/settings`, `PUT /api/settings`

The interface keeps a browser-local fallback for previewing when the Java server or MySQL is unavailable. OCR uses Tesseract.js from a CDN, so expiry image scanning requires internet access.

## Project layout

```text
database/schema.sql
src/main/java/com/stockroom/StockroomApplication.java
src/main/resources/public/index.html
pom.xml
run.bat
```
