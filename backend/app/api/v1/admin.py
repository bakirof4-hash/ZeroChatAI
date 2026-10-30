from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, Request, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.deps import get_client_ip, get_current_admin, get_current_super_admin
from app.db.session import get_db
from app.models.user import User
from app.schemas.admin import (
    AuditLogResponse,
    BadgeUpdateRequest,
    SecurityEventResponse,
    SmtpTestRequest,
    StatusUpdateRequest,
    SystemStatsResponse,
)
from app.schemas.user import UserResponse
from app.services.admin_service import AdminService
from app.services.email_service import EmailService

router = APIRouter(prefix="/admin", tags=["Super Admin Panel"])


@router.get("/stats", response_model=SystemStatsResponse)
async def get_dashboard_stats(
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Retrieves high-level telemetry and health metrics for the admin dashboard."""
    return await AdminService.get_system_stats(db)


@router.get("/users", response_model=List[UserResponse])
async def list_users(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    search: Optional[str] = None,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Lists users with pagination and search filter.
    Protected by backend authorization.
    """
    users, _ = await AdminService.list_users(db, skip=skip, limit=limit, search=search)
    return [UserResponse.model_validate(u) for u in users]


@router.post("/users/{user_id}/badge", response_model=UserResponse)
async def toggle_user_verification_badge(
    user_id: int,
    payload: BadgeUpdateRequest,
    request: Request,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Assigns or revokes verification badge through authorized server-side action.
    - Prevents ordinary users from assigning badges.
    - Protected with audit logging.
    - Protects founder badge from revocation.
    """
    ip = get_client_ip(request)
    updated = await AdminService.toggle_verified_badge(
        db=db,
        actor=admin,
        target_user_id=user_id,
        is_verified_badge=payload.is_verified_badge,
        reason=payload.reason,
        ip_address=ip
    )
    return UserResponse.model_validate(updated)


@router.post("/users/{user_id}/status", response_model=UserResponse)
async def toggle_user_active_status(
    user_id: int,
    payload: StatusUpdateRequest,
    request: Request,
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Activates or bans a user account.
    Revokes active sessions upon banning.
    """
    ip = get_client_ip(request)
    updated = await AdminService.toggle_user_status(
        db=db,
        actor=admin,
        target_user_id=user_id,
        is_active=payload.is_active,
        reason=payload.reason,
        ip_address=ip
    )
    return UserResponse.model_validate(updated)


@router.get("/audit-logs", response_model=List[AuditLogResponse])
async def get_audit_logs(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Fetches chronological audit logs of administrative actions."""
    logs = await AdminService.get_audit_logs(db, skip=skip, limit=limit)
    return [AuditLogResponse.model_validate(l) for l in logs]


@router.get("/security-events", response_model=List[SecurityEventResponse])
async def get_security_events(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """Fetches security monitoring events and failed login telemetry."""
    events = await AdminService.get_security_events(db, skip=skip, limit=limit)
    return [SecurityEventResponse.model_validate(e) for e in events]


@router.post("/test-smtp", response_model=dict)
async def test_smtp_delivery(
    payload: SmtpTestRequest,
    admin: User = Depends(get_current_super_admin)
):
    """
    Tests SMTP configuration by dispatching a test verification code.
    Accessible exclusively by Super Admin / Founder.
    """
    sent, err = await EmailService.send_verification_code(
        to_email=payload.target_email,
        code="123456",
        purpose="REGISTRATION"
    )
    if not sent:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"SMTP test failed: {err}"
        )
    return {"message": f"SMTP test verification email dispatched successfully to {payload.target_email}"}

