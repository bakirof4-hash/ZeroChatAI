from datetime import datetime, timezone
from sqlalchemy import Boolean, Column, DateTime, ForeignKey, Integer, String, Text
from app.db.base import Base


class AppVersion(Base):
    __tablename__ = "app_versions"

    id = Column(Integer, primary_key=True, index=True)
    version_code = Column(Integer, unique=True, nullable=False, index=True)
    version_name = Column(String(50), nullable=False)
    release_notes = Column(Text, nullable=False)

    apk_url = Column(String(500), nullable=False)
    apk_sha256 = Column(String(64), nullable=False)
    apk_size = Column(Integer, nullable=False)

    min_supported_version_code = Column(Integer, default=1, nullable=False)
    is_mandatory = Column(Boolean, default=False, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)

    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False, index=True)
    published_by = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

