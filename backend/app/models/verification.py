from datetime import datetime, timezone
from enum import Enum
from sqlalchemy import Boolean, Column, DateTime, Integer, String
from app.db.base import Base


class CodePurpose(str, Enum):
    REGISTRATION = "REGISTRATION"
    PASSWORD_RESET = "PASSWORD_RESET"


class VerificationCode(Base):
    __tablename__ = "verification_codes"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String(255), index=True, nullable=False)
    code_hash = Column(String(255), nullable=False)  # Argon2id or secure hash
    purpose = Column(String(50), default=CodePurpose.REGISTRATION.value, nullable=False)

    attempts = Column(Integer, default=0, nullable=False)
    max_attempts = Column(Integer, default=5, nullable=False)
    expires_at = Column(DateTime, nullable=False)
    is_used = Column(Boolean, default=False, nullable=False)

    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)

    def is_expired(self) -> bool:
        now = datetime.now(timezone.utc)
        # Ensure timezone compatibility
        if self.expires_at.tzinfo is None:
            return datetime.utcnow() > self.expires_at
        return now > self.expires_at

    def has_exceeded_attempts(self) -> bool:
        return self.attempts >= self.max_attempts

