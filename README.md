# ThinkMart

ThinkMart is a Java 17 / Spring Boot storefront with a responsive HTML, CSS, and JavaScript frontend. Products, accounts, orders, and contact messages are handled by the Java server and saved in MySQL. The cart stays in the shopper's browser until checkout. Checkout calculates prices on the server and never requests or processes payment details.

## Run locally

Install Java 17 or newer. From the `C:\WEB` workspace folder, start the app with the included Maven Wrapper:

```text
cd ECommerce
Copy-Item .env.example .env
# Edit .env and set MYSQL_PASSWORD to your local MySQL password.
.\mvnw.cmd spring-boot:run
```

If the wrapper reports that `JAVA_HOME` is missing, set it to your JDK installation directory before running the command (for example, in PowerShell: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"`). The wrapper downloads Maven and project dependencies on its first run.

ThinkMart connects to the local MySQL server at `localhost:3306` using the `thinkmart` database by default. It loads optional credentials from `.env` (excluded from Git); `.env.example` shows the settings. You can also override `MYSQL_HOST`, `MYSQL_PORT`, `MYSQL_DATABASE`, `MYSQL_USER`, and `MYSQL_PASSWORD` in the same terminal. The MySQL account needs permission to create the database on first startup and to create and modify tables. Alternatively, create the database beforehand and use an account with access to it:

```sql
CREATE DATABASE thinkmart CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

The app creates its tables and sample catalog automatically. Do not commit or share your database password. Existing data in the old local H2 file is not migrated automatically; that file remains untouched. For a remote MySQL server, configure `MYSQL_URL` with the TLS settings required by your provider.

Open [http://localhost:8080](http://localhost:8080). Keep the app running while you shop; stop it with Ctrl+C. Build and run the automated tests with:

```text
.\mvnw.cmd test
```

## Demo behavior

- Search, filter, and sort the sample product catalog.
- Add products to a browser-persisted cart and adjust item quantities.
- Check out as a guest or while signed in. The server validates delivery details, looks up current product prices, recalculates totals, and saves each order.
- Create an account and sign in to see orders placed by that account. Passwords are salted and hashed by the server; session credentials are kept in HTTP-only cookies.
- Submit contact messages to the server.
- Payment is not collected or processed; order confirmation is for demonstration only. Guest orders are saved without being linked to an account.

The sample catalog, external product photography, local MySQL database, and contact form are demo resources, not a production fulfillment or support system. Do not enter real payment details or reuse a real account password. H2 is used only for automated tests.
