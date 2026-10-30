from datetime import datetime, timezone
from sqlalchemy import Column, DateTime, Integer, String, Text
from app.db.base import Base


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(Integer, primary_key=True, index=True)
    timestamp = Column(DateTime, default=lambda: datetime.now(timezone.utc), nullable=False, index=True)

    actor_id = Column(Integer, nullable=True)
    actor_email = Column(String(255), nullable=False)
    action = Column(String(100), nullable=False)

    target_id = Column(Integer, nullable=True)
    target_email = Column(String(255), nullable=True)

    details = Column(Text, nullable=True)
    ip_address = Column(String(100), nullable=True)

