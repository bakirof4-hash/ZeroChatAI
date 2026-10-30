from datetime import datetime, timezone
from enum import Enum
from sqlalchemy import Boolean, Column, DateTime, Integer, String
from app.db.base import Base


class UserRole(str, Enum):
    SUPER_ADMIN = "SUPER_ADMIN"  # Reserved exclusively for Founder
    ADMIN = "ADMIN"
    USER = "USER"


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    email = Column(String(255), unique=True, index=True, nullable=False)
    username = Column(String(100), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)

    # Role & Permissions
    role = Column(String(50), default=UserRole.USER.value, nullable=False)
    is_founder = Column(Boolean, default=False, nullable=False)
    is_verified_badge = Column(Boolean, default=False, nullable=False)  # Blue checkmark ✓

    # Account Status
    is_active = Column(Boolean, default=True, nullable=False)
    email_verified = Column(Boolean, default=False, nullable=False)

    # Timestamps
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    last_login_at = Column(DateTime, nullable=True)

    def is_super_admin(self) -> bool:
        return self.role == UserRole.SUPER_ADMIN.value or self.is_founder

    def is_admin(self) -> bool:
        return self.role in [UserRole.SUPER_ADMIN.value, UserRole.ADMIN.value]

