# Pavti API — Knowledge guide

Everything a developer needs to understand, change and extend the backend.
Read the [README](../README.md) first to get it running.

---

## 1. The life of one request

Example: a waiter taps **Save order** on the phone.

```
POST /api/Order/Save   Authorization: Bearer <token>   { tableId: 3, lines: [...] }
   │
   ├─ ApiCryptoFilter         opens the encrypted body (section 3a); encrypts the answer at the end
   ├─ RequestLogFilter        writes method, URL, status, time to api_request_logs (passwords masked)
   ├─ TokenInterceptor        sees @RequirePermission("ORDERS_TAKE") on the method:
   │     1. reads the JWT (signature + expiry)
   │     2. session still open?  (user_sessions — logout ends it at once)
   │     3. person active?  business not paused?
   │     4. does this person have the ORDERS_TAKE tick?  (owners always do)
   │     → puts CurrentUser on the request
   ├─ OrderController.save    only passes the request to the BAL
   ├─ OrderBal.save           all rules: lock table, menu price for staff, discount right,
   │                          bill math (BillingCalculator), table status, history
   ├─ OrderDal / TableDal …   CALL sp_order_save_header(...), CALL sp_order_item_add(...)
   └─ ApiResponse.ok(order)   { "Success": true, "StatusCode": 200, "Message": "Order saved", "Data": {...} }
```

If anything goes wrong, the code **throws** and `GlobalExceptionHandler` answers:

| Thrown | Response |
|---|---|
| `ApiException.badRequest("Add at least one dish.")` | 400 with that message |
| `SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '...'` in a procedure | 400 with that message (`DataClass` translates it) |
| `ApiException.unauthorized / forbidden / notFound / conflict` | 401 / 403 / 404 / 409 |
| `@Valid` failure on a request record | 400 with the field message |
| anything unexpected | 500 "Something went wrong", full details saved in `error_logs` |

---

## 2. Login (authentication)

| Step | What happens |
|---|---|
| **Sign up** | `Auth/SendOtp {mobileNo, purpose:"REGISTER"}` → 6-digit OTP (BCrypt-hashed in `user_otps`, 5 min, 3 tries). In dev mode the OTP comes back as `devOtp`. Then `Auth/Register {businessName, ownerName, mobileNo, password, otp}` creates the business, its owner, a FREE plan subscription and a "Takeaway" customer in one transaction, and signs in. |
| **Login** | `Auth/Login {loginId, password}`. loginId is a mobile number (any format: "+91 98200 11111") or an email. 5 wrong passwords → locked for 15 minutes. Every try is written to `login_history`. |
| **Tokens** | `accessToken` (JWT, 12 h) carries `sub`=userId, `bid`=businessId, `role`, `sid`=sessionId. `refreshToken` is random; only its SHA-256 is stored in `user_sessions`. `Auth/Refresh` gives a new access token. |
| **Logout** | closes the session row, so the same token stops working at once. Turning a person off, or the owner setting a new password for them, closes all their sessions. |
| **Forgot password** | `SendOtp` with `RESET_PASSWORD`, then `Auth/ResetPassword`. All sessions are closed. |

Passwords use BCrypt (cost 12). The database never holds a plain password or OTP.

---

## 3. Rights (authorization) — three layers

**1. Role page rights** (`role_page_rights`): for each role and page, five flags:
view / add / edit / delete / export. Roles are `SUPER_ADMIN` (platform admin), `OWNER`, `STAFF`.

**2. Staff ticks** (`user_permissions`): the owner ticks what each staff member may do.

| Code | Meaning |
|---|---|
| `ORDERS_TAKE` | take orders, make bills |
| `PAYMENTS_TAKE` | mark bills paid (cash / UPI) |
| `DISCOUNTS_GIVE` | give a discount |
| `BILLS_CANCEL` | cancel a bill |
| `SALES_VIEW` | see everyone's sales (without it: only their own work on Home) |

A page can need a tick (`pages.required_permission_code`). For example, the PAYMENTS page needs
`PAYMENTS_TAKE`, so a waiter never sees it in the menu. Owners and the admin have every tick.

**3. Business separation:** every restaurant procedure takes `p_business_id`, and the BAL always
gets it from the token (`user.requireBusinessId()`), **never from the request body**. One
restaurant can never read another's data, even by guessing ids.

### How to protect an API

```java
@ValidateEncryptedRequest                              // body must come encrypted (section 3a) — on every controller
@ValidateToken                                         // just signed in
@RequirePage(page = "TABLES", action = PageAction.ADD) // role page right (+ the page's tick)
@RequirePermission("PAYMENTS_TAKE")                    // staff tick
@RequireRole("OWNER")                                  // only this role
```

When the right depends on the request (Save = ADD for new, EDIT for existing), check it in the BAL:
`rights.requirePage(user, "MENU", isNew ? PageAction.ADD : PageAction.EDIT)`.

`Menu/GetMenuBinding` returns the same rights to React, which builds the side menu and hides buttons.
The server **always checks again**. Hiding a button is only for looks.

---

## 3a. Encryption of every API call

Same idea as Health 360 (RSA + AES for every request), with stronger settings. It lives in its own
place on each side, and no controller, BAL or page knows about it:

| Side | Folder | Files |
|---|---|---|
| Java | `src/main/java/com/pavti/crypto/` + `filter/ValidateEncryptedRequest` | `ApiCryptoFilter` (decrypts requests, encrypts answers), `EncryptedRequestInterceptor` (checks `@ValidateEncryptedRequest`), `CryptoHelper` (the ciphers), `RsaKeyHolder` (the server key), `CryptoController` (`/api/Crypto/GetPublicKey`), `EncryptedRequest` / `EncryptedResponse` |
| React | `pavti/src/utils/crypto/` | `encryption.ts`, `decryption.ts`, `serverKey.ts`, `aad.ts`, `base64.ts`, `types.ts`, `index.ts`; wired in once in `services/BaseService.ts` |

**Marked like `@ValidateToken`** (Health 360: `[ValidateEncryptedRequest]`). Every controller has it on the
class, right under `@RequestMapping`:

```java
@RestController
@RequestMapping("/api/Audit")
@ValidateEncryptedRequest          // ← all APIs in this controller must be encrypted
public class AuditController extends BaseApiController {
```

- The **filter** opens and seals the body for every call; the controller code never changes.
- The **annotation** says the API must be encrypted. With `mode: required`, a plain call to it is refused
  with "This server only accepts encrypted requests."
- **You cannot forget it:** `ControllerEncryptionTest` fails the build (`mvnw test`) if any controller
  in `com.pavti.controller` is missing it. (In Health 360 a missing attribute silently left an API open.)
- Only `/api/Crypto/GetPublicKey` has no annotation, on purpose: it gives out the public key that
  encryption starts with.

**How one call is protected**

```
Browser                                                   Server
1. gets the RSA public key once  ── /Crypto/GetPublicKey ──▶  (never sends the private key)
2. makes a NEW random AES-256 key for this call
3. body JSON → AES-256-GCM (random 12-byte IV)
4. AES key → RSA-OAEP-SHA256 (only the server can open it)
5. sends {EncryptedAESKey, EncryptedData, IV, KeyId}
   + header X-Pavti-Encrypted: 1                ─────────▶  ApiCryptoFilter opens it → controller gets
                                                            the plain JSON as usual
                                                ◀─────────  answer JSON → AES-GCM with the SAME key,
6. opens {EncryptedData, IV} with its AES key                a NEW IV → {EncryptedData, IV}
```

- **AES-GCM** detects any change to the data on the way (Health 360 used AES-CBC, which does not).
- The API name ("Order/Save") is bound into the encryption, so a body copied from one API is
  refused by another.
- The **private key is never in the code** (Health 360 had it hard-coded). The server uses
  `PAVTI_RSA_PRIVATE_KEY` (PEM text) if set; otherwise `keys/pavti_rsa_private.pem`, which it
  creates on first start. `keys/` is in `.gitignore`. **Back this file up**, and use the same key on
  every server behind one address.
- If the key changes, the server answers `409 KEY_CHANGED`. The app fetches the new key once and
  repeats the call by itself, so users notice nothing.
- The request log (`api_request_logs`) stores the **decrypted** body with passwords and OTPs as `***`.

**Settings**

| Where | Setting | Values |
|---|---|---|
| Java | `pavti.crypto.mode` / `PAVTI_CRYPTO_MODE` | `optional` (default: accepts encrypted and plain, so Swagger and curl work) · `required` (plain requests refused, use in production) · `off` |
| React | `VITE_ENCRYPT_API` | `true` by default when `VITE_USE_MOCK_API=false`; mock mode is never encrypted |

> Browsers allow WebCrypto only on **https://** or **localhost**. If a phone opens the app as
> `http://192.168.x.x`, the app sends plain requests (a warning is shown in the console), and a
> `required` server refuses them. In a restaurant, serve the app over https.

> Encryption is an extra layer **on top of HTTPS, not instead of it**. HTTPS also protects the
> token and the page itself.

**Make a new server key** (for example if the old one leaked): stop the server, delete
`keys/pavti_rsa_private.pem` (or change `PAVTI_RSA_PRIVATE_KEY`), and start it again. Open apps pick
up the new key by themselves.

## 4. Restaurant rules (what the BAL enforces)

These match the React mock (`pavti/src/services/mock/handlers.ts`), so the demo and the real
server behave the same.

**Orders and bills** (`OrderBal`)
- An order stays `OPEN` while dishes are added. **Make bill** gives it the next number
  (`prefix + 00001`). The procedure locks the business row, so two cashiers never get the same number.
- Staff always get the **menu price** (or the price already on that open order). Only the owner can
  change a price or add a custom line ("Parcel charge").
- A discount needs `DISCOUNTS_GIVE` and is written to history with its reason.
- Staff always get the business's service charge %; the owner can change it per bill (0–20%).
- A table can have only one order. Saving locks the table row, so two waiters cannot both open it.
- A table follows its order: `OPEN` → OCCUPIED, `BILLED`/`PART_PAID` → BILL_PENDING,
  `PAID`/`CANCELLED` → AVAILABLE.
- A made bill cannot be changed. To cancel it you need `BILLS_CANCEL` and a reason, and any
  money taken must be refunded first.

**Bill math** (`BillingCalculator`, the same as React `utils/billing.ts`)
1. Line amount = quantity × price, in whole **paise** (no float rounding errors).
2. The discount (percent or amount, never more than the bill) is shared over the lines by value;
   the last line takes the leftover paisa.
3. GST per line on (line − its discount share), grouped by rate for the bill footer.
4. Service charge % on (subtotal − discount).
5. Total = subtotal − discount + GST + service charge.

Example: 4 × Masala Dosa ₹180, GST 5%, service 5% → 720 + 36 + 36 = **₹792**.

**Payments** (`PaymentBal`). Pavti does not move money. It only records what was received
outside the app (cash in hand, UPI scanner).
- The bill must be made first. Amount > 0 and not more than the balance due.
- Cash: if "cash given" is entered, the change is worked out (given − amount).
- Every tap sends a unique `requestKey`, which is UNIQUE in the database. A double tap can never
  save twice; the second one gets "This payment is already saved."
- The order row is locked while paying, so two phones cannot both take the last balance.
- Status after money: paid ≥ total → `PAID`, some → `PART_PAID`, refunded to 0 → `REFUNDED`.
- **Refund**: owner only, with a reason. A new `REFUND` row is added; the original payment is never
  changed or deleted.

**Plans** limit active people (`max_users`) and tables (`max_tables`).

**Business day** closes at 4 AM (Asia/Kolkata), so a 1:30 AM bill counts for the evening before.
All DATETIME columns are stored in UTC; the API sends ISO text like `2026-10-20T09:15:00Z`.

---

## 5. Database rules

- Every table has `is_active, inserted_by, inserted_date, updated_by, updated_date`.
- **No foreign keys.** Links are plain id columns, checked in procedures and the BAL.
- **Nothing is deleted.** Removing sets `is_active = 0`. Removed tables and menu groups are renamed
  (`Table 3 #17`) so the name can be used again.
- All SQL is in **stored procedures** (`sp_<area>_<action>`, parameters `p_...`). Java never builds
  SQL strings, so SQL injection cannot happen.
- Procedures do **not** start their own transactions. Java's `@Transactional` does it. A procedure
  with its own COMMIT would silently commit the Java transaction halfway.
- **Never edit a migration that has already run.** Flyway checks a checksum and refuses to start.
  Add a new file `V8__what_it_does.sql` instead (`DROP PROCEDURE IF EXISTS ...; CREATE PROCEDURE ...`).

---

## 6. How to add a new feature (example: "day close")

1. **SQL** — new file `src/main/resources/db/migration/V8__sp_day_close.sql`:
   ```sql
   CREATE PROCEDURE sp_day_close_save(IN p_business_id BIGINT, IN p_business_date DATE, IN p_by BIGINT)
   BEGIN
     IF EXISTS (SELECT 1 FROM day_closings WHERE business_id = p_business_id AND business_date = p_business_date) THEN
       SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'This day is already closed.';
     END IF;
     INSERT INTO day_closings (business_id, business_date, inserted_by) VALUES (p_business_id, p_business_date, p_by);
     SELECT LAST_INSERT_ID() AS day_closing_id;
   END;
   ```
2. **DataObject** — `dataobject/dayclose/DayCloseRequest.java` (a Java `record`). For rows, name
   the fields like the columns in camelCase (`business_date` → `businessDate`); `RecordRowMapper`
   fills them.
3. **DAL** — `dal/DayCloseDal.java`: `db.getLong("sp_day_close_save", bid, date, by)`. The
   parameter order is the procedure's.
4. **BAL** — `bal/DayCloseBal.java`: rules, `audit.log(...)`, `@Transactional` if it writes more
   than one thing.
5. **Controller** — `controller/DayCloseController.java`, `@RequestMapping("/api/DayClose")` and
   `@ValidateEncryptedRequest` on the class (the build fails without it),
   `@RequirePage(page = "DAY_CLOSE", action = PageAction.ADD)` on the method.
6. **React** — `src/services/DayCloseService.ts` calling `/DayClose/Save`, plus the same handler in
   the mock so the demo keeps working.
7. Restart. Flyway runs V8 and the endpoint appears in Swagger.

---

## 7. Going live checklist

- [ ] `PAVTI_JWT_SECRET` set to a long random value (not the default)
- [ ] `PAVTI_OTP_DEV_MODE=false` and an SMS provider added in `AuthBal.sendOtp` (marked TODO)
- [ ] the database user has a strong password; MySQL is not open to the internet
- [ ] `PAVTI_CRYPTO_MODE=required`, and `keys/pavti_rsa_private.pem` (or `PAVTI_RSA_PRIVATE_KEY`) backed up
- [ ] HTTPS in front of the API (nginx / a cloud load balancer)
- [ ] `PAVTI_CORS_ORIGINS` = only your real website address
- [ ] the first admin signed in once and changed the password; then remove `PAVTI_ADMIN_PASSWORD`
- [ ] daily MySQL backup (`mysqldump pavti`)
