from unittest.mock import patch
import pytest
from fastapi import HTTPException
from app.services.google_auth_service import GoogleAuthService


@pytest.mark.asyncio
async def test_google_auth_empty_token_rejected(client):
    """Ensure empty or too short id_token is rejected."""
    res = await client.post("/api/v1/auth/google", json={"id_token": "short"})
    assert res.status_code == 422


@pytest.mark.asyncio
async def test_google_auth_new_user_registration(client, db_session):
    """Test successful first-time registration via Google ID Token."""
    mock_claims = {
        "google_id": "google_sub_1001",
        "email": "newuser@gmail.com",
        "email_verified": True,
        "name": "New Google User",
        "picture": "https://lh3.googleusercontent.com/avatar/newuser.png",
        "given_name": "New",
        "family_name": "User"
    }

    with patch.object(GoogleAuthService, "verify_id_token", return_value=mock_claims):
        res = await client.post(
            "/api/v1/auth/google",
            json={"id_token": "mock_google_id_token_1001"}
        )

    assert res.status_code == 200
    data = res.json()
    assert "access_token" in data
    assert "session_token" in data
    user = data["user"]
    assert user["email"] == "newuser@gmail.com"
    assert user["google_id"] == "google_sub_1001"
    assert user["full_name"] == "New Google User"
    assert user["avatar_url"] == "https://lh3.googleusercontent.com/avatar/newuser.png"
    assert user["role"] == "USER"
    assert user["is_founder"] is False
    assert user["is_verified_badge"] is False
    assert user["email_verified"] is True


@pytest.mark.asyncio
async def test_google_auth_relogin_does_not_duplicate_user(client, db_session):
    """Test that logging in multiple times with the same Google ID reuses the existing account."""
    mock_claims = {
        "google_id": "google_sub_1002",
        "email": "relogin_user@gmail.com",
        "email_verified": True,
        "name": "Relogin User",
        "picture": None,
        "given_name": "Relogin",
        "family_name": "User"
    }

    with patch.object(GoogleAuthService, "verify_id_token", return_value=mock_claims):
        res1 = await client.post(
            "/api/v1/auth/google",
            json={"id_token": "mock_google_id_token_1002"}
        )
        assert res1.status_code == 200
        user1 = res1.json()["user"]

        res2 = await client.post(
            "/api/v1/auth/google",
            json={"id_token": "mock_google_id_token_1002"}
        )
        assert res2.status_code == 200
        user2 = res2.json()["user"]

    assert user1["id"] == user2["id"]
    assert user1["email"] == user2["email"]


@pytest.mark.asyncio
async def test_google_auth_founder_bakirof4_privileges(client, db_session):
    """
    Test Requirement 9:
    Founder bakirof4@gmail.com privileges (SUPER_ADMIN, is_founder, is_verified_badge)
    are granted ONLY after email ownership is proven via verified Google token.
    """
    founder_claims = {
        "google_id": "google_founder_id_9999",
        "email": "bakirof4@gmail.com",
        "email_verified": True,
        "name": "Founder Bakirov",
        "picture": "https://lh3.googleusercontent.com/avatar/founder.png",
        "given_name": "Founder",
        "family_name": "Bakirov"
    }

    with patch.object(GoogleAuthService, "verify_id_token", return_value=founder_claims):
        res = await client.post(
            "/api/v1/auth/google",
            json={"id_token": "mock_founder_id_token"}
        )

    assert res.status_code == 200
    data = res.json()
    user = data["user"]
    assert user["email"] == "bakirof4@gmail.com"
    assert user["role"] == "SUPER_ADMIN"
    assert user["is_founder"] is True
    assert user["is_verified_badge"] is True


@pytest.mark.asyncio
async def test_google_auth_unverified_email_rejected(client, db_session):
    """Ensure Google accounts with unverified emails are forbidden."""
    with patch.object(
        GoogleAuthService,
        "verify_id_token",
        side_effect=HTTPException(status_code=403, detail="Google account email is unverified.")
    ):
        res = await client.post(
            "/api/v1/auth/google",
            json={"id_token": "mock_unverified_token"}
        )

    assert res.status_code == 403
    assert "unverified" in res.json()["detail"]


def test_google_auth_service_claims_check():
    """Directly test GoogleAuthService token requirements."""
    with pytest.raises(HTTPException) as exc_info:
        GoogleAuthService.verify_id_token("   ")
    assert exc_info.value.status_code == 400


@pytest.mark.asyncio
async def test_passwordless_login_flow(client, db_session):
    """Test zero-password email code login flow for devices lacking Google Play Services."""
    email = "huawei_user@example.com"

    # 1. Request code for purpose="LOGIN"
    res = await client.post("/api/v1/auth/send-code", json={"email": email, "purpose": "LOGIN"})
    assert res.status_code == 200
    dev_code = res.json()["dev_code"]

    # 2. Verify code
    res_verif = await client.post(
        "/api/v1/auth/verify-code",
        json={"email": email, "code": dev_code, "purpose": "LOGIN"}
    )
    assert res_verif.status_code == 200
    token = res_verif.json()["verification_token"]

    # 3. Passwordless login
    res_login = await client.post(
        "/api/v1/auth/passwordless-login",
        json={"email": email, "verification_token": token}
    )
    assert res_login.status_code == 200
    data = res_login.json()
    assert "access_token" in data
    assert "session_token" in data
    assert data["user"]["email"] == email
    assert data["user"]["email_verified"] is True

