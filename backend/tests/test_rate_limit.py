import pytest
from app.core.rate_limiter import email_code_limiter


@pytest.mark.asyncio
async def test_resend_cooldown_enforcement(client):
    """Test that requesting code twice immediately triggers a 429 with remaining cooldown."""
    email = "cooldown_test@example.com"
    email_code_limiter.reset(f"cooldown:{email}:REGISTRATION")
    email_code_limiter.reset(f"rate:{email}:REGISTRATION")

    # 1. First request -> 200 OK
    res1 = await client.post("/api/v1/auth/send-code", json={"email": email, "purpose": "REGISTRATION"})
    assert res1.status_code == 200

    # 2. Second request immediately -> 429 Too Many Requests
    res2 = await client.post("/api/v1/auth/send-code", json={"email": email, "purpose": "REGISTRATION"})
    assert res2.status_code == 429
    assert "Please wait" in res2.json()["detail"]


@pytest.mark.asyncio
async def test_hourly_rate_limit(client):
    """Test that requesting more than MAX_CODE_REQUESTS_PER_HOUR results in 429."""
    email = "ratelimit_test@example.com"
    rate_key = f"rate:{email}:REGISTRATION"
    email_code_limiter.reset(rate_key)

    # Artificially trigger limit
    for _ in range(5):
        email_code_limiter.record_request(rate_key)

    res = await client.post("/api/v1/auth/send-code", json={"email": email, "purpose": "REGISTRATION"})
    assert res.status_code == 429
    assert "Too many verification code requests" in res.json()["detail"]

