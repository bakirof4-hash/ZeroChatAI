import asyncio
import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Optional, Tuple
import httpx
from app.core.config import settings

logger = logging.getLogger("zerochat.email")


class EmailService:
    """
    Unified Email Delivery Service for ZeroChat AI.
    
    Supports:
    1. Resend HTTPS REST API (Recommended for Cloud Hosting / Render — bypasses blocked SMTP ports).
    2. Standard SMTP / Gmail with TLS/SSL.
    
    SECURITY PRINCIPLES:
    - Credentials are loaded strictly from environment variables (Settings).
    - API keys and passwords are NEVER hardcoded or exposed in error messages.
    - Verification codes and credentials are NEVER logged to server logs.
    - Graceful fallback with detailed configuration diagnostics.
    """

    @classmethod
    def is_resend_configured(cls) -> bool:
        """Checks if Resend API key is configured."""
        return bool(settings.RESEND_API_KEY and settings.RESEND_API_KEY.strip())

    @classmethod
    def is_smtp_configured(cls) -> bool:
        """Checks if standard SMTP credentials are fully provided."""
        return bool(
            settings.SMTP_HOST
            and settings.SMTP_USER
            and settings.SMTP_PASSWORD
            and settings.SMTP_ENABLED
        )

    @classmethod
    def is_configured(cls) -> bool:
        """Returns True if either Resend HTTPS API or SMTP is configured."""
        return cls.is_resend_configured() or cls.is_smtp_configured()

    @classmethod
    def get_provider_name(cls) -> str:
        """Returns the active delivery provider ('resend', 'smtp', or 'none')."""
        if cls.is_resend_configured():
            return "resend"
        elif cls.is_smtp_configured():
            return "smtp"
        return "none"

    @classmethod
    def _get_from_header(cls) -> str:
        """Determines sender 'From' address."""
        if settings.SMTP_FROM_EMAIL and settings.SMTP_FROM_EMAIL.strip():
            from_email = settings.SMTP_FROM_EMAIL.strip()
            if "<" in from_email and ">" in from_email:
                return from_email
            return f"{settings.SMTP_FROM_NAME} <{from_email}>"
        elif cls.is_resend_configured():
            # Official Resend testing sender address
            return f"{settings.SMTP_FROM_NAME} <onboarding@resend.dev>"
        elif settings.SMTP_USER:
            return f"{settings.SMTP_FROM_NAME} <{settings.SMTP_USER}>"
        return f"{settings.SMTP_FROM_NAME} <noreply@zerochat.ai>"

    @classmethod
    def get_configuration_diagnostics(cls) -> str:
        """Returns clear actionable instructions for setting up email delivery."""
        instructions = (
            "\n[ZeroChat AI - Email Configuration Guide]\n"
            "To enable live email delivery to user inboxes, choose one option:\n\n"
            "OPTION 1: Resend HTTPS API (Recommended for Render / Cloud Hosting):\n"
            "  1. Sign up free at https://resend.com\n"
            "  2. Create an API Key at https://resend.com/api-keys\n"
            "  3. Set environment variable: RESEND_API_KEY=re_your_api_key\n"
            "  4. (Optional) Set SMTP_FROM_EMAIL to your verified domain (defaults to onboarding@resend.dev)\n\n"
            "OPTION 2: Gmail SMTP:\n"
            "  1. Go to Google Account -> Security -> 2-Step Verification\n"
            "  2. Create an App Password at https://myaccount.google.com/apppasswords\n"
            "  3. Set environment variables:\n"
            "       SMTP_HOST=smtp.gmail.com\n"
            "       SMTP_PORT=587\n"
            "       SMTP_USER=your_email@gmail.com\n"
            "       SMTP_PASSWORD=your_16_character_app_password\n"
            "       SMTP_ENABLED=True\n"
        )
        return instructions

    @classmethod
    async def _send_resend_async(
        cls,
        to_email: str,
        subject: str,
        html_body: str,
        text_body: str
    ) -> Tuple[bool, Optional[str]]:
        """Sends an email via Resend HTTPS REST API."""
        if not cls.is_resend_configured():
            return False, "Resend API key is not configured."

        from_header = cls._get_from_header()
        payload = {
            "from": from_header,
            "to": [to_email],
            "subject": subject,
            "html": html_body,
            "text": text_body
        }
        headers = {
            "Authorization": f"Bearer {settings.RESEND_API_KEY.strip()}",
            "Content-Type": "application/json"
        }

        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                response = await client.post(
                    "https://api.resend.com/emails",
                    headers=headers,
                    json=payload
                )

                if 200 <= response.status_code < 300:
                    logger.info("Successfully dispatched email to %s via Resend HTTPS API", to_email)
                    return True, None
                else:
                    try:
                        err_data = response.json()
                        err_msg = err_data.get("message", response.text)
                    except Exception:
                        err_msg = response.text
                    logger.error("Resend API error (%s): %s", response.status_code, err_msg)
                    return False, f"Resend API error ({response.status_code}): {err_msg}"
        except Exception as e:
            err = f"Resend API network error: {str(e)}"
            logger.error("Resend delivery failed for %s: %s", to_email, str(e))
            return False, err

    @classmethod
    def _send_smtp_sync(
        cls,
        to_email: str,
        subject: str,
        html_body: str,
        text_body: str
    ) -> Tuple[bool, Optional[str]]:
        """Synchronous SMTP delivery executed in threadpool."""
        if not cls.is_smtp_configured():
            diag = cls.get_configuration_diagnostics()
            logger.warning("SMTP not configured. Message to %s was not sent.\n%s", to_email, diag)
            return False, f"SMTP service not configured on server. Required settings: {diag}"

        from_email = settings.SMTP_FROM_EMAIL or settings.SMTP_USER
        from_header = f"{settings.SMTP_FROM_NAME} <{from_email}>"

        msg = MIMEMultipart("alternative")
        msg["Subject"] = subject
        msg["From"] = from_header
        msg["To"] = to_email

        # Attach text and HTML parts
        msg.attach(MIMEText(text_body, "plain", "utf-8"))
        msg.attach(MIMEText(html_body, "html", "utf-8"))

        try:
            if settings.SMTP_PORT == 465:
                # SSL
                with smtplib.SMTP_SSL(settings.SMTP_HOST, settings.SMTP_PORT, timeout=15) as server:
                    server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
                    server.send_message(msg)
            else:
                # STARTTLS (default for Gmail port 587)
                with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT, timeout=15) as server:
                    server.ehlo()
                    if settings.SMTP_USE_TLS:
                        server.starttls()
                        server.ehlo()
                    server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
                    server.send_message(msg)

            logger.info("Successfully dispatched email to %s via %s", to_email, settings.SMTP_HOST)
            return True, None
        except smtplib.SMTPAuthenticationError as e:
            err = f"SMTP Authentication failed. Verify Google App Password in .env: {str(e)}"
            logger.error(err)
            return False, err
        except Exception as e:
            err = f"SMTP Transmission error: {str(e)}"
            logger.error(err)
            return False, err

    @classmethod
    async def send_verification_code(
        cls,
        to_email: str,
        code: str,
        purpose: str = "REGISTRATION"
    ) -> Tuple[bool, Optional[str]]:
        """
        Dispatches a 6-digit verification code email.
        Uses Resend HTTPS API if configured, with automatic fallback to SMTP.
        """
        purpose_label = "Account Registration" if purpose == "REGISTRATION" else "Password Reset"
        subject = f"Your ZeroChat AI Verification Code: {code}"

        text_body = (
            f"ZeroChat AI - {purpose_label}\n\n"
            f"Your one-time verification code is:\n\n"
            f"    {code}\n\n"
            f"This code will expire in {settings.VERIFICATION_CODE_EXPIRE_MINUTES} minutes.\n\n"
            f"Security Notice:\n"
            f"- Never share this code with anyone.\n"
            f"- ZeroChat AI staff will NEVER ask for your password or verification code.\n"
            f"- If you did not request this code, you can safely ignore this email.\n"
        )

        html_body = f"""
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <style>
            body {{
              font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
              background-color: #0A0E17;
              color: #E2E8F0;
              margin: 0;
              padding: 40px 20px;
            }}
            .card {{
              max-width: 520px;
              margin: 0 auto;
              background: #131B2A;
              border: 1px solid #1E293B;
              border-radius: 16px;
              padding: 36px;
              box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
            }}
            .header {{
              display: flex;
              align-items: center;
              margin-bottom: 24px;
            }}
            .logo-text {{
              font-size: 24px;
              font-weight: 800;
              color: #00D2FF;
              letter-spacing: -0.5px;
            }}
            .subtitle {{
              color: #94A3B8;
              font-size: 14px;
              margin-bottom: 28px;
            }}
            .code-box {{
              background: #0E1626;
              border: 2px dashed #0077FE;
              border-radius: 12px;
              text-align: center;
              padding: 24px;
              margin: 28px 0;
            }}
            .code {{
              font-size: 38px;
              font-weight: 900;
              letter-spacing: 10px;
              color: #00D2FF;
              font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
            }}
            .expiry {{
              font-size: 13px;
              color: #64748B;
              margin-top: 8px;
            }}
            .warning {{
              background: #1A1F2C;
              border-left: 4px solid #F59E0B;
              padding: 12px 16px;
              border-radius: 6px;
              font-size: 12px;
              color: #CBD5E1;
              line-height: 1.5;
            }}
            .footer {{
              text-align: center;
              font-size: 12px;
              color: #475569;
              margin-top: 32px;
            }}
          </style>
        </head>
        <body>
          <div class="card">
            <div class="logo-text">ZeroChat AI</div>
            <div class="subtitle">{purpose_label} Verification</div>
            <p>Please enter the following 6-digit verification code in the ZeroChat AI mobile application to proceed:</p>
            
            <div class="code-box">
              <div class="code">{code}</div>
              <div class="expiry">Expires in {settings.VERIFICATION_CODE_EXPIRE_MINUTES} minutes</div>
            </div>

            <div class="warning">
              <strong>Security reminder:</strong> Never share this code with anyone. ZeroChat AI will never contact you asking for your verification code or Gmail password.
            </div>

            <div class="footer">
              &copy; 2026 ZeroChat AI &bull; Encrypted &amp; Secure Authentication System
            </div>
          </div>
        </body>
        </html>
        """

        # 1. Prefer Resend HTTPS API if configured
        if cls.is_resend_configured():
            sent, err = await cls._send_resend_async(to_email, subject, html_body, text_body)
            if sent:
                return True, None
            # Fallback to SMTP if Resend fails and SMTP is enabled
            if cls.is_smtp_configured():
                logger.warning("Resend delivery failed (%s). Falling back to SMTP...", err)
                return await asyncio.to_thread(cls._send_smtp_sync, to_email, subject, html_body, text_body)
            return False, err

        # 2. Use standard SMTP if enabled
        elif cls.is_smtp_configured():
            return await asyncio.to_thread(cls._send_smtp_sync, to_email, subject, html_body, text_body)

        # 3. Unconfigured fallback
        diag = cls.get_configuration_diagnostics()
        logger.warning("Email delivery not configured. Verification code to %s was not dispatched.\n%s", to_email, diag)
        return False, f"Email delivery service not configured on server. Required settings: {diag}"
