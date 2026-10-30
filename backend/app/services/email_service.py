import asyncio
import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Optional, Tuple
from app.core.config import settings

logger = logging.getLogger("zerochat.email")


class EmailService:
    """
    SMTP Email Delivery Service for ZeroChat AI.
    
    SECURITY PRINCIPLES:
    1. Credentials are loaded strictly from environment variables (Settings).
    2. Users are NEVER asked for their email provider password.
    3. For Gmail SMTP, Google App Passwords must be used (not account master password).
    4. Safe fallback / dry-run with detailed setup diagnostics when SMTP is unconfigured.
    """

    @staticmethod
    def is_configured() -> bool:
        """Checks if SMTP credentials are fully provided."""
        return bool(
            settings.SMTP_HOST
            and settings.SMTP_USER
            and settings.SMTP_PASSWORD
            and settings.SMTP_ENABLED
        )

    @classmethod
    def get_configuration_diagnostics(cls) -> str:
        """Returns clear actionable instructions for setting up Gmail SMTP."""
        missing = []
        if not settings.SMTP_USER:
            missing.append("SMTP_USER (Your Gmail address, e.g. youremail@gmail.com)")
        if not settings.SMTP_PASSWORD:
            missing.append("SMTP_PASSWORD (A 16-character Google App Password)")
        if not settings.SMTP_ENABLED:
            missing.append("SMTP_ENABLED (Set to True in backend .env)")

        instructions = (
            "\n[ZeroChat AI - SMTP Configuration Guide]\n"
            "To enable live Gmail delivery:\n"
            "1. Go to your Google Account -> Security (https://myaccount.google.com/security)\n"
            "2. Turn on 2-Step Verification if not already enabled.\n"
            "3. Search for 'App Passwords' (https://myaccount.google.com/apppasswords).\n"
            "4. Create a new app password named 'ZeroChat AI'.\n"
            "5. Copy the generated 16-character code (e.g. 'abcd efgh ijkl mnop').\n"
            "6. In backend/.env, set:\n"
            "     SMTP_HOST=smtp.gmail.com\n"
            "     SMTP_PORT=587\n"
            "     SMTP_USER=your_email@gmail.com\n"
            "     SMTP_PASSWORD=abcdefghijklmnop\n"
            "     SMTP_FROM_EMAIL=your_email@gmail.com\n"
            "     SMTP_ENABLED=True\n"
            f"Missing settings: {', '.join(missing) if missing else 'None'}\n"
        )
        return instructions

    @classmethod
    def _send_smtp_sync(cls, to_email: str, subject: str, html_body: str, text_body: str) -> Tuple[bool, Optional[str]]:
        """Synchronous SMTP delivery executed in threadpool."""
        if not cls.is_configured():
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
    async def send_verification_code(cls, to_email: str, code: str, purpose: str = "REGISTRATION") -> Tuple[bool, Optional[str]]:
        """Asynchronously dispatches a 6-digit verification code email."""
        purpose_label = "Account Registration" if purpose == "REGISTRATION" else "Password Reset"
        subject = f"Your ZeroChat AI Verification Code: {code}"

        text_body = (
            f"ZeroChat AI - {purpose_label}\n\n"
            f"Your one-time verification code is:\n\n"
            f"    {code}\n\n"
            f"This code will expire in {settings.VERIFICATION_CODE_EXPIRE_MINUTES} minutes.\n"
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

        # Run SMTP in a separate thread so asyncio loop is never blocked
        return await asyncio.to_thread(
            cls._send_smtp_sync, to_email, subject, html_body, text_body
        )

