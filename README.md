# ZeroChat AI — Gmail Authentication, Security & Founder System

ZeroChat AI is a secure, encrypted mobile communication platform built with **Kotlin & Jetpack Compose** on Android and powered by a **FastAPI & PostgreSQL / SQLite** backend.

The platform provides a zero-password-trust Gmail email verification architecture, cryptographic Argon2id password hashing, robust rate-limiting, an immutable **Founder Account & Super Admin System** with a distinct **Blue Verified Badge (✓)**, unique username validation, user QR code generation & deep link scanning, persistent 1-to-1 direct messaging, and an **offline zero-execution file security analyzer** for APK and EXE binaries.

---

## 1. System Architecture

```
[ ZeroChat AI Android App (Jetpack Compose) ]
                  │
                  ▼ HTTPS / JSON REST API
[ FastAPI Backend (Port 8000) ]
   ├── Argon2id Password Hasher (RFC 9106)
   ├── Sliding Window Rate Limiter & Cooldown Tracker
   ├── Role-Based Access Control (SUPER_ADMIN / ADMIN / USER)
   ├── Audit Logging & Security Telemetry
   ├── Offline Static Binary Analyzer (APK / PE EXE)
   ├── QR Code Generator & Deep Link Resolver
   ├── Direct 1-to-1 & Public Chat Messaging Engine
   │
   ├── [ Database Layer ]
   │      └── PostgreSQL / Async SQLite (SQLAlchemy 2.0 & Alembic)
   │
   └── [ SMTP Gateway ]
          └── Gmail SMTP (smtp.gmail.com:587 STARTTLS / Google App Password)
```

---

## 2. Core Security & Authentication Specifications

### 2.1. Gmail Registration & Zero-Password Trust
- **Never Ask for User Gmail Password:** ZeroChat AI strictly guarantees that users are **never** prompted for their Google account password.
- **Backend-Only SMTP:** Outgoing emails are dispatched through server-side environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`).
- **Google App Password Requirement:** For Gmail SMTP, a 16-character **Google App Password** is used, never an account master password.
- **6-Digit Cryptographic Code:** Generated using cryptographically secure randomness (`secrets.randbelow`).
- **Resend Cooldown & Rate Limits:** 60-second cooldown between requests and a strict limit of 5 code requests per hour per email.

### 2.2. Code Verification
- **5-Minute Expiry:** Verification codes automatically expire 5 minutes after issuance.
- **Max 5 Attempt Lockout:** Code is immediately invalidated if 5 incorrect attempts are made.
- **Immediate Invalidation:** Codes are marked `is_used = True` upon successful verification to prevent replay attacks.
- **Non-Disclosure of Registration Status:** Requesters cannot probe or enumerate registered emails; identical generic responses are returned on password resets.

### 2.3. Account Login & Session Security
- **Argon2id Password Hashing:** Passwords are never stored in plaintext. They are hashed using Argon2id (`time_cost=3`, `memory_cost=64MB`, `parallelism=4`).
- **Stateful & Stateless Security:** Authenticates with Argon2id and issues signed JWT access tokens tied to revocable server-side `UserSession` records.
- **Global Invalidation on Password Reset:** Resetting a password automatically revokes all existing sessions across all devices.

---

## 3. Founder Account, Blue Verified Badge & Super Admin

### 3.1. Founder Identity
- **Owner Role:** The founder is the owner of ZeroChat AI and holds the exclusive `SUPER_ADMIN` role.
- **Blue Verified Badge (✓):** A luminous blue verified badge with an electric cyan border and "FOUNDER" indicator appears consistently across:
  - Chat message bubbles & avatars
  - User search results & directory
  - User profile screens
  - Group chats & member lists
- **Immutability:** The founder account and badge cannot be deleted, banned, or revoked by any user or standard admin.

### 3.2. Dedicated Super Admin Dashboard
Accessible exclusively by the authenticated Founder:
1. **Overview & Telemetry:** Total users, verified badges, active sessions, security incident counts, and live SMTP connectivity.
2. **User Management:** Live directory search, server-side badge grant/revoke, and account suspension (ban/unban).
3. **Security Monitoring:** Telemetry stream of failed logins, rate-limit triggers, expired code attempts, and suspicious IPs.
4. **Audit Logs:** Immutable audit trail documenting every administrative action with actor, target, timestamp, and action description.
5. **SMTP & Settings:** Status check for Gmail SMTP delivery and diagnostics.

### 3.3. Backend RBAC Enforcement
- Ordinary users attempting to access `/api/v1/admin/*` endpoints receive an immediate `403 Forbidden` response.
- Verification badges can **only** be granted through authorized server-side administrative actions.

---

## 4. Usernames, QR Codes, and Persistent Messaging

### 4.1. Unique Usernames
- Server-side format enforcement (3-30 characters, alphanumeric and underscore).
- Dedicated live validation endpoint: `GET /api/v1/auth/check-username?username=...`.
- Duplicate username registration is rejected with `400 Bad Request`.

### 4.2. QR Code Generation & Scanning
- Unique QR code generated for every user:
  - Deep link format: `zerochat://user?u={username}&uid={user_id}`
  - Streamable PNG endpoint: `GET /api/v1/users/{username}/qr.png`
- In-app QR Scanner & Resolver (`POST /api/v1/users/qr-scan`) resolves users and verifies contact permissions.
- Contact blocking (`POST /api/v1/users/{user_id}/block`): Prevents unwanted messages and deep link connection attempts.

### 4.3. Direct 1-to-1 & Public Messaging
- Database-backed message persistence (`chat_messages` table).
- Full sender profile metadata, including Founder Blue Verified Badge `✓`.
- Real-time block checks before message delivery: blocked contacts are rejected with `403 Forbidden`.
- File attachment upload with automatic static security inspection.

---

## 5. Offline File Security Analyzer (Zero Cloud AI Dependency)

ZeroChat AI includes an offline, heuristic static binary inspector:
- **APK Inspection (No Installation):** Safely parses ZIP headers, `AndroidManifest.xml`, and `classes.dex` without executing code. Detects high-risk permissions (`SYSTEM_ALERT_WINDOW`, `RECEIVE_BOOT_COMPLETED`, `SEND_SMS`, `REQUEST_INSTALL_PACKAGES`).
- **EXE Inspection (No Execution):** Safely parses MS-DOS MZ headers, PE COFF headers, optional headers, and section tables. Detects code-injection APIs (`VirtualAllocEx`, `WriteProcessMemory`, `CreateRemoteThread`, `SetWindowsHookEx`) and calculates Shannon entropy (packed/obfuscated sections).
- **Honest Static-Analysis Disclaimer:** Clearly displays that heuristic analysis does not execute code and cannot guarantee 100% detection of novel, polymorphic malware.

---

## 6. Setup & Verification Instructions

### 6.1. Step 1: Configure Gmail SMTP with Google App Password
1. Visit your Google Account Security Settings: [https://myaccount.google.com/security](https://myaccount.google.com/security)
2. Ensure **2-Step Verification** is enabled.
3. Open App Passwords: [https://myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
4. Enter `ZeroChat AI` as the application name and click **Create**.
5. Copy the generated 16-character code (e.g., `abcd efgh ijkl mnop`).
6. In `backend/.env`, configure:
   ```env
   SMTP_HOST=smtp.gmail.com
   SMTP_PORT=587
   SMTP_USER=your_email@gmail.com
   SMTP_PASSWORD=abcdefghijklmnop
   SMTP_FROM_EMAIL=your_email@gmail.com
   SMTP_ENABLED=True
   ```

### 6.2. Step 2: Backend Deployment & Migrations
```powershell
cd backend
.\.venv\Scripts\activate

# 1. Run Alembic migrations
python -m alembic upgrade head

# 2. Provision the Founder Super Admin account
python -m app.cli create-founder --email "founder@zerochat.ai" --password "FounderMaster2026!" --username "ZeroFounder"

# 3. Check SMTP connectivity
python -m app.cli check-smtp

# 4. Start the FastAPI server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Swagger UI: `http://localhost:8000/docs`

### 6.3. Step 3: Run Backend Automated Tests (19 Passed)
```powershell
cd backend
python -m pytest -v
```

### 6.4. Step 4: Build & Install Android APK
```powershell
cd android

# Build debug APK
.\gradlew assembleDebug

# Install APK on connected device / emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Output APK location: `android/app/build/outputs/apk/debug/app-debug.apk`
