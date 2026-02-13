# 🛡️ Covenant - Secure Escrow Platform API

> **A production-grade backend for secure peer-to-peer transactions.** > *Built with Spring Boot 3, Stripe, and JWT Security.*

## 📖 Overview

**Covenant** is a trust-minimized platform that acts as a financial middleman between strangers. It solves the "Problem of Trust" in online marketplaces:
* **Buyers** don't want to pay before receiving the item.
* **Sellers** don't want to ship before getting paid.

Covenant holds the funds in a secure **Escrow State** (`LOCKED`) and only releases them to the seller when the buyer confirms satisfaction, or automatically after a 14-day dispute period.

---

## 🚀 Key Features

* **🔐 JWT Authentication:** Stateless security with Bearer tokens for session management.
* **💳 Stripe Integration:** Full payment lifecycle handling using `PaymentIntent` (SCA-compliant).
* **🔄 State Machine Logic:** Robust contract lifecycle (`DRAFT` → `PAYMENT_PENDING` → `LOCKED` → `SHIPPED` → `DELIVERED` → `SATISFIED`).
* **👮 Role-Based Access Control:** * Only **Sellers** can ship items.
    * Only **Buyers** can confirm payments.
    * Users cannot accept their own contracts.
* **⏰ Automated Scheduling:** Background jobs (Cron) to auto-release funds for stale contracts after 14 days.
* **📧 Email Notifications:** Real-time alerts via JavaMailSender (SMTP) for every status change.

---

## 🛠️ Tech Stack

* **Core Framework:** Java 17, Spring Boot 3.2
* **Security:** Spring Security, JWT (JSON Web Tokens)
* **Database:** PostgreSQL / MySQL (JPA/Hibernate)
* **Payments:** Stripe API
* **Documentation:** OpenAPI (Swagger UI)
* **Build Tool:** Maven

---

## 🔌 API Documentation (Swagger UI)

Since this project is a backend-first solution, a frontend is not required to test the full logic. We use **Swagger UI** for interactive testing.

**URL:** `http://localhost:8080/swagger-ui/index.html`

### 🔑 How to Authenticate in Swagger
1.  **Register/Login** via the `AuthController` endpoints.
2.  Copy the `token` string from the response.
3.  Click the **Authorize 🔓** button at the top right of the Swagger page.
4.  Paste the token (e.g., `eyJhbGciOi...`).
5.  Click **Authorize**. Now all your requests are secured!

---

## 🧪 Examiner Demo Script (The "Happy Path")

Follow this sequence to demonstrate the full lifecycle of a transaction:

### Phase 1: Setup
1.  **Register Seller:** `POST /api/auth/register` (`role: SELLER`)
2.  **Register Buyer:** `POST /api/auth/register` (`role: BUYER`)
3.  **Login Seller:** Get the JWT Token.

### Phase 2: Contract Creation
4.  **Create Contract (as Seller):** * `POST /api/contracts`
    * Body: `{"title": "Gaming Laptop", "amount": 50000, "description": "Mint condition"}`
    * *Copy the `id` from the response.*

### Phase 3: Payment (The Core Logic)
5.  **Login Buyer:** Switch JWT Token.
6.  **Accept Contract:** * `POST /api/contracts/{id}/accept`
    * *Result:* Returns `clientSecret` (Stripe Payment Intent). Status moves to `PAYMENT_PENDING`.
7.  **Confirm Payment:** * `POST /api/contracts/{id}/confirm-payment`
    * *Result:* Status moves to `LOCKED`. **Funds are now safe.**

### Phase 4: Fulfillment
8.  **Login Seller:** Switch JWT Token.
9.  **Ship Item:** * `POST /api/contracts/{id}/ship`
    * Body: `{"trackingId": "FX-123456", "logisticsProvider": "FedEx"}`
    * *Result:* Status moves to `SHIPPED`.

### Phase 5: Completion
10. **Login Buyer:** Switch Token.
11. **Mark Satisfied:** * `POST /api/contracts/{id}/satisfy`
    * *Result:* Status `SATISFIED`. **Funds are released to Seller.**

---

## ⚙️ Configuration

To run this locally, you must configure your environment variables in `src/main/resources/application.yaml` or via your IDE run configuration.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/covenant_db
    username: your_db_user
    password: your_db_password
  mail:
    host: smtp.gmail.com
    username: your_email@gmail.com
    password: your_app_password

stripe:
  key:
    secret: sk_test_...  # Your Stripe Secret Key
    publishable: pk_test_...