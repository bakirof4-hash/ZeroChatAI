from datetime import datetime, timezone
from sqlalchemy import Column, DateTime, Integer, String, Text
from app.db.base import Base


class SecurityEvent(Base):
    __tablename__ = "security_events"

    id = Column(Integer, primary_key=True, index=True)
    timestamp = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False, index=True)

    event_type = Column(String(100), nullable=False)
    email = Column(String(255), nullable=True, index=True)
    ip_address = Column(String(100), nullable=True)
    severity = Column(String(50), default="INFO", nullable=False)  # INFO, WARNING, SUSPICIOUS, CRITICAL
    details = Column(Text, nullable=True)

