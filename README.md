# Hotel Booking Portal

## Running the backend

Prerequisites: Java 21, Maven, and a running MySQL 8 database. Copy `.env.example` to `.env` and set `DB_URL`, `DB_USER`, and `DB_PASSWORD` for your local database. From the repository root, load those values into the shell and start Spring Boot:

```sh
set -a; source .env; set +a
cd backend
mvn spring-boot:run
```

The backend uses Flyway for schema changes and Hibernate `validate` to check the schema. Before the first migration is added, Flyway starts normally against an empty database; baseline-on-migrate is enabled for an existing non-empty schema.

## Running the frontend

Prerequisites: Node.js and npm, plus the backend running on port 8080. From the repository root:

```sh
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. During local development, Vite proxies `/api` requests to `http://localhost:8080`. `VITE_API_BASE_URL` is optional; set it only when the API is hosted at another base URL.
