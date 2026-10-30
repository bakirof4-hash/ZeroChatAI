from datetime import datetime, timedelta, timezone
import pytest
from sqlalchemy import select
from app.core.security import hash_password, verify_password
from app.models.user import User
from app.models.verification import CodePurpose, VerificationCode


@pytest.mark.asyncio
async def test_email_validation(client):
    """Test that invalid email format is rejected with 422."""
    res = await client.post("/api/v1/auth/send-code", json={"email": "not-an-email", "purpose": "REGISTRATION"})
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_send_and_verify_code(client, db_session):
    """Test sending a 6-digit code, verifying it, and ensuring it is invalidated after use."""
    email = "user1@example.com"

    # 1. Request verification code
    res = await client.post("/api/v1/auth/send-code", json={"email": email, "purpose": "REGISTRATION"})
    assert res.status_code == 200
    data = res.json()
    assert data["cooldown_seconds"] == 60
    assert data["expires_in_seconds"] == 300
    dev_code = data.get("dev_code")
    assert dev_code is not None
    assert len(dev_code) == 6
    assert dev_code.isdigit()

    # 2. Verify with wrong code
    res_wrong = await client.post(
        "/api/v1/auth/verify-code",
        json={"email": email, "code": "000000", "purpose": "REGISTRATION"}
    )
    assert res_wrong.status_code == 400
    assert "Incorrect verification code" in res_wrong.json()["detail"]

    # 3. Verify with correct code
    res_correct = await client.post(
        "/api/v1/auth/verify-code",
        json={"email": email, "code": dev_code, "purpose": "REGISTRATION"}
    )
    assert res_correct.status_code == 200
    verif_data = res_correct.json()
    assert "verification_token" in verif_data
    token = verif_data["verification_token"]

    # 4. Attempt to reuse code (must be invalidated!)
    res_reuse = await client.post(
        "/api/v1/auth/verify-code",
        json={"email": email, "code": dev_code, "purpose": "REGISTRATION"}
    )
    assert res_reuse.status_code == 400
    assert "Invalid or expired" in res_reuse.json()["detail"]

    # 5. Create account password using Argon2id
    reg_res = await client.post(
        "/api/v1/auth/create-password",
        json={
            "verification_token": token,
            "email": email,
            "username": "TestUser",
            "password": "Password123!",
            "confirm_password": "Password123!"
        }
    )
    assert reg_res.status_code == 201
    auth_data = reg_res.json()
    assert "access_token" in auth_data
    assert auth_data["user"]["email"] == email
    assert auth_data["user"]["is_verified_badge"] is False
    assert auth_data["user"]["is_founder"] is False

    # Check password in database is Argon2id hash
    stmt = select(User).where(User.email == email)
    db_res = await db_session.execute(stmt)
    user_db = db_res.scalar_one()
    assert "$argon2id$" in user_db.hashed_password
    assert verify_password("Password123!", user_db.hashed_password)


@pytest.mark.asyncio
async def test_code_expiration(client, db_session):
    """Test that codes expired after 5 minutes cannot be verified."""
    email = "expired_test@example.com"

    # Directly create an expired code in DB
    past_expiry = datetime.now(timezone.utc) - timedelta(minutes=1)
    expired_entry = VerificationCode(
        email=email,
        code_hash=hash_password("654321"),
        purpose="REGISTRATION",
        attempts=0,
        max_attempts=5,
        expires_at=past_expiry,
        is_used=False
    )
    db_session.add(expired_entry)
    await db_session.commit()

    res = await client.post(
        "/api/v1/auth/verify-code",
        json={"email": email, "code": "654321", "purpose": "REGISTRATION"}
    )
    assert res.status_code == 400
    assert "expired" in res.json()["detail"].lower()


@pytest.mark.asyncio
async def test_login_flow(client, db_session):
    """Test successful login, failed login, and session validation."""
    email = "login_test@example.com"
    hashed = hash_password("SecurePassword2026!")

    test_user = User(
        email=email,
        username="LoginUser",
        hashed_password=hashed,
        role="USER",
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(test_user)
    await db_session.commit()

    # 1. Invalid password
    fail_res = await client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": "WrongPassword!"}
    )
    assert fail_res.status_code == 401

    # 2. Correct password
    success_res = await client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": "SecurePassword2026!"}
    )
    assert success_res.status_code == 200
    data = success_res.json()
    token = data["access_token"]
    assert token is not None

    # 3. Access /auth/me with bearer token
    me_res = await client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert me_res.status_code == 200
    assert me_res.json()["email"] == email


@pytest.mark.asyncio
async def test_non_disclosure_of_unregistered_email_on_password_reset(client):
    """Password reset code request does not disclose if an email is not registered."""
    res = await client.post(
        "/api/v1/auth/send-code",
        json={"email": "nonexistent@example.com", "purpose": "PASSWORD_RESET"}
    )
    assert res.status_code == 200
    assert "If the email address is associated" in res.json()["message"]

