from datetime import datetime
from typing import List, Optional
from pydantic import BaseModel, ConfigDict, EmailStr
from app.schemas.user import UserResponse


class BadgeUpdateRequest(BaseModel):
    is_verified_badge: bool
    reason: str


class StatusUpdateRequest(BaseModel):
    is_active: bool
    reason: str


class RoleUpdateRequest(BaseModel):
    role: str
    reason: str


class AuditLogResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    timestamp: datetime
    actor_email: str
    action: str
    target_email: Optional[str] = None
    details: Optional[str] = None
    ip_address: Optional[str] = None


class SecurityEventResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    timestamp: datetime
    event_type: str
    email: Optional[str] = None
    ip_address: Optional[str] = None
    severity: str
    details: Optional[str] = None


class SystemStatsResponse(BaseModel):
    total_users: int
    verified_users: int
    admin_users: int
    active_sessions: int
    total_security_events: int
    smtp_enabled: bool
    smtp_host: str
    app_version: str


class SmtpTestRequest(BaseModel):
    target_email: EmailStr

