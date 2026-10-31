import pytest
import httpx
from unittest.mock import patch, MagicMock, AsyncMock
from app.core.config import settings
from app.services.email_service import EmailService


@pytest.mark.asyncio
async def test_email_service_is_configured_modes():
    # 1. Unconfigured
    with patch.object(settings, "RESEND_API_KEY", None), \
         patch.object(settings, "SMTP_ENABLED", False):
        assert EmailService.is_resend_configured() is False
        assert EmailService.is_smtp_configured() is False
        assert EmailService.is_configured() is False
        assert EmailService.get_provider_name() == "none"

    # 2. Resend configured
    with patch.object(settings, "RESEND_API_KEY", "re_test_key_123456"), \
         patch.object(settings, "SMTP_ENABLED", False):
        assert EmailService.is_resend_configured() is True
        assert EmailService.is_smtp_configured() is False
        assert EmailService.is_configured() is True
        assert EmailService.get_provider_name() == "resend"

    # 3. SMTP configured
    with patch.object(settings, "RESEND_API_KEY", None), \
         patch.object(settings, "SMTP_ENABLED", True), \
         patch.object(settings, "SMTP_HOST", "smtp.gmail.com"), \
         patch.object(settings, "SMTP_USER", "user@gmail.com"), \
         patch.object(settings, "SMTP_PASSWORD", "app_password_16ch"):
        assert EmailService.is_resend_configured() is False
        assert EmailService.is_smtp_configured() is True
        assert EmailService.is_configured() is True
        assert EmailService.get_provider_name() == "smtp"


@pytest.mark.asyncio
async def test_from_header_formats():
    # Custom sender address
    with patch.object(settings, "SMTP_FROM_EMAIL", "noreply@zerochat.ai"), \
         patch.object(settings, "SMTP_FROM_NAME", "ZeroChat Security"):
        assert EmailService._get_from_header() == "ZeroChat Security <noreply@zerochat.ai>"

    # Resend default testing address
    with patch.object(settings, "SMTP_FROM_EMAIL", None), \
         patch.object(settings, "RESEND_API_KEY", "re_123"), \
         patch.object(settings, "SMTP_FROM_NAME", "ZeroChat Security"):
        assert EmailService._get_from_header() == "ZeroChat Security <onboarding@resend.dev>"


@pytest.mark.asyncio
async def test_resend_send_success():
    captured_request = {}

    def mock_post(url, headers, json):
        captured_request["url"] = str(url)
        captured_request["headers"] = headers
        captured_request["json"] = json
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"id": "re_msg_abc123"}
        return mock_resp

    with patch.object(settings, "RESEND_API_KEY", "re_secret_test_key"), \
         patch.object(settings, "SMTP_FROM_EMAIL", "verify@zerochat.ai"), \
         patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_async_post:
        
        mock_async_post.side_effect = lambda url, headers, json: mock_post(url, headers, json)

        sent, err = await EmailService.send_verification_code(
            to_email="newuser@example.com",
            code="123456",
            purpose="REGISTRATION"
        )

        assert sent is True
        assert err is None
        assert captured_request["url"] == "https://api.resend.com/emails"
        assert captured_request["headers"]["Authorization"] == "Bearer re_secret_test_key"
        assert captured_request["headers"]["Content-Type"] == "application/json"
        assert captured_request["json"]["to"] == ["newuser@example.com"]
        assert "123456" in captured_request["json"]["text"]
        assert "123456" in captured_request["json"]["html"]
        assert "Registration" in captured_request["json"]["subject"] or "Verification" in captured_request["json"]["subject"]


@pytest.mark.asyncio
async def test_resend_send_api_error_response():
    mock_resp = MagicMock()
    mock_resp.status_code = 422
    mock_resp.json.return_value = {"message": "Domain not verified"}
    mock_resp.text = '{"message": "Domain not verified"}'

    with patch.object(settings, "RESEND_API_KEY", "re_invalid_key"), \
         patch.object(settings, "SMTP_ENABLED", False), \
         patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_async_post:
        
        mock_async_post.return_value = mock_resp

        sent, err = await EmailService.send_verification_code(
            to_email="test@example.com",
            code="654321",
            purpose="REGISTRATION"
        )

        assert sent is False
        assert "Resend API error (422)" in err
        assert "Domain not verified" in err


@pytest.mark.asyncio
async def test_resend_send_network_failure():
    with patch.object(settings, "RESEND_API_KEY", "re_test_key"), \
         patch.object(settings, "SMTP_ENABLED", False), \
         patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_async_post:
        
        mock_async_post.side_effect = httpx.ConnectError("Connection timed out")

        sent, err = await EmailService.send_verification_code(
            to_email="test@example.com",
            code="999888",
            purpose="PASSWORD_RESET"
        )

        assert sent is False
        assert "Resend API network error" in err
        assert "Connection timed out" in err


@pytest.mark.asyncio
async def test_resend_fallback_to_smtp_on_failure():
    # When Resend fails but SMTP is configured, it should automatically fallback to SMTP
    mock_resp = MagicMock()
    mock_resp.status_code = 500
    mock_resp.text = "Internal server error"
    mock_resp.json.side_effect = Exception("Not JSON")

    with patch.object(settings, "RESEND_API_KEY", "re_test_key"), \
         patch.object(settings, "SMTP_ENABLED", True), \
         patch.object(settings, "SMTP_HOST", "smtp.gmail.com"), \
         patch.object(settings, "SMTP_USER", "sender@gmail.com"), \
         patch.object(settings, "SMTP_PASSWORD", "app_password"), \
         patch("httpx.AsyncClient.post", new_callable=AsyncMock) as mock_async_post, \
         patch.object(EmailService, "_send_smtp_sync", return_value=(True, None)) as mock_smtp_sync:
        
        mock_async_post.return_value = mock_resp

        sent, err = await EmailService.send_verification_code(
            to_email="fallback@example.com",
            code="112233",
            purpose="REGISTRATION"
        )

        assert sent is True
        assert err is None
        mock_smtp_sync.assert_called_once()


@pytest.mark.asyncio
async def test_unconfigured_email_diagnostics():
    with patch.object(settings, "RESEND_API_KEY", None), \
         patch.object(settings, "SMTP_ENABLED", False):
        
        sent, err = await EmailService.send_verification_code(
            to_email="test@example.com",
            code="000111"
        )

        assert sent is False
        assert "not configured on server" in err
        assert "RESEND_API_KEY" in err
        assert "SMTP_HOST" in err

