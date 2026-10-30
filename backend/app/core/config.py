import secrets
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore"
    )

    APP_NAME: str = "ZeroChat AI"
    APP_VERSION: str = "1.0.0"
    ENVIRONMENT: str = "development"
    DEBUG: bool = True

    # Database
    DATABASE_URL: str = "sqlite+aiosqlite:///./zerochat.db"

    # Supabase Configuration
    # Store credentials ONLY in backend/.env (never in frontend/Android APK)
    SUPABASE_URL: Optional[str] = None
    SUPABASE_ANON_KEY: Optional[str] = None
    SUPABASE_SERVICE_ROLE_KEY: Optional[str] = None
    SUPABASE_STORAGE_BUCKET: str = "zerochat-attachments"

    @property
    def async_database_url(self) -> str:
        """
        Normalizes postgres:// and postgresql:// to postgresql+asyncpg://
        for seamless Supabase PostgreSQL connection compatibility with SQLAlchemy async.
        """
        url = self.DATABASE_URL
        if url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql+asyncpg://", 1)
        elif url.startswith("postgresql://") and not url.startswith("postgresql+asyncpg://"):
            url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
        return url

    # Security & JWT
    SECRET_KEY: str = "zerochat-insecure-dev-key-change-in-production-minimum-32-chars-long"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 * 7  # 7 days
    SESSION_EXPIRE_DAYS: int = 30

    # Verification Code Policy
    VERIFICATION_CODE_EXPIRE_MINUTES: int = 5
    MAX_VERIFICATION_ATTEMPTS: int = 5
    RESEND_COOLDOWN_SECONDS: int = 60
    MAX_CODE_REQUESTS_PER_HOUR: int = 5

    # Rate Limiting
    MAX_LOGIN_ATTEMPTS_PER_15MIN: int = 5

    # SMTP Configuration
    # Store credentials ONLY in environment variables
    # For Gmail SMTP, use a Google App Password (never regular account password)
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USER: Optional[str] = None
    SMTP_PASSWORD: Optional[str] = None
    SMTP_FROM_EMAIL: Optional[str] = None
    SMTP_FROM_NAME: str = "ZeroChat AI Security"
    SMTP_USE_TLS: bool = True
    SMTP_ENABLED: bool = False  # Set to True when valid credentials are provided

    # Founder / Super Admin Provisioning
    FOUNDER_EMAIL: str = "founder@zerochat.ai"
    FOUNDER_PASSWORD: Optional[str] = None
    FOUNDER_USERNAME: str = "ZeroFounder"
    FOUNDER_AUTO_PROVISION: bool = True


settings = Settings()

