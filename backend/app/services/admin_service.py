from datetime import datetime, timezone
from typing import List, Optional, Tuple
from fastapi import HTTPException, status
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.config import settings
from app.core.security import hash_password
from app.models.audit_log import AuditLog
from app.models.security_event import SecurityEvent
from app.models.session import UserSession
from app.models.user import User, UserRole
from app.schemas.admin import SystemStatsResponse


class AdminService:
    @classmethod
    async def provision_founder_account(
        cls,
        db: AsyncSession,
        email: str,
        password: str,
        username: str = "ZeroFounder"
    ) -> User:
        """
        Secure founder provisioning service.
        Creates or updates the Founder account with:
        - SUPER_ADMIN role
        - is_founder = True
        - is_verified_badge = True (Blue checkmark ✓)
        - Argon2id hashed password
        """
        clean_email = email.strip().lower()

        stmt = select(User).where(User.email == clean_email)
        result = await db.execute(stmt)
        user = result.scalar_one_or_none()

        hashed = hash_password(password)

        if user:
            # Update existing user to founder status, preserving their existing password
            user.role = UserRole.SUPER_ADMIN.value
            user.is_founder = True
            user.is_verified_badge = True
            user.is_active = True
            user.email_verified = True
            if not user.hashed_password:
                user.hashed_password = hashed
        else:
            user = User(
                email=clean_email,
                username=username,
                hashed_password=hashed,
                role=UserRole.SUPER_ADMIN.value,
                is_founder=True,
                is_verified_badge=True,
                is_active=True,
                email_verified=True
            )
            db.add(user)

        # Audit log entry for founder provisioning
        audit = AuditLog(
            actor_id=user.id if user.id else 0,
            actor_email=clean_email,
            action="FOUNDER_PROVISIONED",
            target_id=user.id,
            target_email=clean_email,
            details="Founder account provisioned with SUPER_ADMIN role and Blue Verification Badge."
        )
        db.add(audit)
        await db.commit()
        await db.refresh(user)
        return user

    @classmethod
    async def list_users(
        cls,
        db: AsyncSession,
        skip: int = 0,
        limit: int = 50,
        search: Optional[str] = None
    ) -> Tuple[List[User], int]:
        """Lists users with pagination and optional search query."""
        base_query = select(User)
        count_query = select(func.count(User.id))

        if search:
            search_pattern = f"%{search.strip().lower()}%"
            base_query = base_query.where(
                (func.lower(User.email).like(search_pattern)) |
                (func.lower(User.username).like(search_pattern))
            )
            count_query = count_query.where(
                (func.lower(User.email).like(search_pattern)) |
                (func.lower(User.username).like(search_pattern))
            )

        base_query = base_query.order_by(User.id.desc()).offset(skip).limit(limit)

        users_res = await db.execute(base_query)
        total_res = await db.execute(count_query)

        users = list(users_res.scalars().all())
        total = total_res.scalar_one()

        return users, total

    @classmethod
    async def toggle_verified_badge(
        cls,
        db: AsyncSession,
        actor: User,
        target_user_id: int,
        is_verified_badge: bool,
        reason: str,
        ip_address: Optional[str] = None
    ) -> User:
        """
        Assigns or revokes verification badge through authorized server-side action.
        Only Admins / Super Admins can invoke this.
        Ordinary users are strictly blocked.
        """
        stmt = select(User).where(User.id == target_user_id)
        res = await db.execute(stmt)
        target = res.scalar_one_or_none()

        if not target:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Target user not found")

        # Founder's badge cannot be revoked
        if target.is_founder and not is_verified_badge:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Founder's blue verification badge cannot be revoked."
            )

        target.is_verified_badge = is_verified_badge

        # Record audit log
        action_name = "ASSIGN_VERIFIED_BADGE" if is_verified_badge else "REVOKE_VERIFIED_BADGE"
        audit = AuditLog(
            actor_id=actor.id,
            actor_email=actor.email,
            action=action_name,
            target_id=target.id,
            target_email=target.email,
            details=f"Badge status updated to {is_verified_badge}. Reason: {reason}",
            ip_address=ip_address
        )
        db.add(audit)
        await db.commit()
        await db.refresh(target)
        return target

    @classmethod
    async def toggle_user_status(
        cls,
        db: AsyncSession,
        actor: User,
        target_user_id: int,
        is_active: bool,
        reason: str,
        ip_address: Optional[str] = None
    ) -> User:
        """Activates or deactivates (bans) a user."""
        stmt = select(User).where(User.id == target_user_id)
        res = await db.execute(stmt)
        target = res.scalar_one_or_none()

        if not target:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Target user not found")

        if target.is_founder:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Founder account cannot be suspended or deactivated."
            )

        target.is_active = is_active

        # If deactivated, revoke all their active sessions immediately
        if not is_active:
            revoke_stmt = (
                update(UserSession)
                .where(UserSession.user_id == target.id)
                .values(is_revoked=True)
            )
            await db.execute(revoke_stmt)

        action_name = "UNBAN_USER" if is_active else "BAN_USER"
        audit = AuditLog(
            actor_id=actor.id,
            actor_email=actor.email,
            action=action_name,
            target_id=target.id,
            target_email=target.email,
            details=f"Active status updated to {is_active}. Reason: {reason}",
            ip_address=ip_address
        )
        db.add(audit)
        await db.commit()
        await db.refresh(target)
        return target

    @classmethod
    async def get_system_stats(cls, db: AsyncSession) -> SystemStatsResponse:
        """Returns high-level application metrics for the Super Admin dashboard."""
        total_users_res = await db.execute(select(func.count(User.id)))
        total_users = total_users_res.scalar_one()

        verified_users_res = await db.execute(
            select(func.count(User.id)).where(User.is_verified_badge == True)
        )
        verified_users = verified_users_res.scalar_one()

        admins_res = await db.execute(
            select(func.count(User.id)).where(User.role.in_([UserRole.SUPER_ADMIN.value, UserRole.ADMIN.value]))
        )
        admin_users = admins_res.scalar_one()

        now = datetime.now(timezone.utc)
        active_sess_res = await db.execute(
            select(func.count(UserSession.id)).where(
                UserSession.is_revoked == False,
                UserSession.expires_at > now
            )
        )
        active_sessions = active_sess_res.scalar_one()

        events_res = await db.execute(select(func.count(SecurityEvent.id)))
        total_events = events_res.scalar_one()

        return SystemStatsResponse(
            total_users=total_users,
            verified_users=verified_users,
            admin_users=admin_users,
            active_sessions=active_sessions,
            total_security_events=total_events,
            smtp_enabled=settings.SMTP_ENABLED,
            smtp_host=settings.SMTP_HOST,
            app_version=settings.APP_VERSION
        )

    @classmethod
    async def get_audit_logs(
        cls,
        db: AsyncSession,
        skip: int = 0,
        limit: int = 50
    ) -> List[AuditLog]:
        """Fetches chronological audit logs of administrative actions."""
        stmt = select(AuditLog).order_by(AuditLog.id.desc()).offset(skip).limit(limit)
        res = await db.execute(stmt)
        return list(res.scalars().all())

    @classmethod
    async def get_security_events(
        cls,
        db: AsyncSession,
        skip: int = 0,
        limit: int = 50
    ) -> List[SecurityEvent]:
        """Fetches security monitoring telemetry and events."""
        stmt = select(SecurityEvent).order_by(SecurityEvent.id.desc()).offset(skip).limit(limit)
        res = await db.execute(stmt)
        return list(res.scalars().all())

