import random
import re
from datetime import datetime, timedelta, timezone
from typing import Optional, Tuple
from fastapi import HTTPException, status
from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession
from app.core.config import settings
from app.core.rate_limiter import email_code_limiter, login_attempt_limiter
from app.core.security import (
    create_access_token,
    decode_access_token,
    generate_6_digit_code,
    generate_session_token,
    hash_password,
    verify_password,
)
from app.models.audit_log import AuditLog
from app.models.security_event import SecurityEvent
from app.models.session import UserSession
from app.models.user import User, UserRole
from app.models.verification import CodePurpose, VerificationCode
from app.services.email_service import EmailService
from app.services.google_auth_service import GoogleAuthService


class AuthService:
    @classmethod
    async def request_verification_code(
        cls,
        db: AsyncSession,
        email: str,
        purpose: str,
        ip_address: Optional[str] = None
    ) -> Tuple[str, int, int, Optional[str]]:
        """
        Generates and sends a 6-digit one-time verification code.
        Enforces cooldown and hourly rate limits.
        Ensures non-disclosure of email registration status.
        """
        clean_email = email.strip().lower()

        # 1. Check cooldown (60 seconds)
        cooldown_key = f"cooldown:{clean_email}:{purpose}"
        is_allowed, remaining_cd = email_code_limiter.check_resend_cooldown(
            cooldown_key, settings.RESEND_COOLDOWN_SECONDS
        )
        if not is_allowed:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Please wait {remaining_cd} seconds before requesting a new verification code."
            )

        # 2. Check rate limit (max 5 per hour)
        rate_key = f"rate:{clean_email}:{purpose}"
        is_within_limit, _ = email_code_limiter.check_rate_limit(
            rate_key, settings.MAX_CODE_REQUESTS_PER_HOUR, 3600
        )
        if not is_within_limit:
            # Log security event
            event = SecurityEvent(
                event_type="RATE_LIMIT_EXCEEDED",
                email=clean_email,
                ip_address=ip_address,
                severity="WARNING",
                details=f"Verification code rate limit exceeded for {purpose}"
            )
            db.add(event)
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many verification code requests. Please try again after 1 hour."
            )

        # 3. Check existing user status for non-disclosure protection
        query = select(User).where(User.email == clean_email)
        result = await db.execute(query)
        existing_user = result.scalar_one_or_none()

        # Record cooldown and rate limit
        email_code_limiter.record_request(cooldown_key)
        email_code_limiter.record_request(rate_key)

        # If user requests PASSWORD_RESET but email does not exist:
        # Do not disclose to unauthorized requesters! Return standard generic message.
        if purpose == CodePurpose.PASSWORD_RESET.value and not existing_user:
            return (
                "If the email address is associated with an account, a verification code has been dispatched.",
                settings.RESEND_COOLDOWN_SECONDS,
                settings.VERIFICATION_CODE_EXPIRE_MINUTES * 60,
                None
            )

        # If user requests REGISTRATION and is already registered:
        if purpose == CodePurpose.REGISTRATION.value and existing_user:
            # Log event and return safe response without leaking details
            event = SecurityEvent(
                event_type="DUPLICATE_REGISTRATION_ATTEMPT",
                email=clean_email,
                ip_address=ip_address,
                severity="INFO",
                details="Registration code requested for existing registered account."
            )
            db.add(event)
            await db.commit()
            return (
                "If the email address is valid, a verification code has been dispatched.",
                settings.RESEND_COOLDOWN_SECONDS,
                settings.VERIFICATION_CODE_EXPIRE_MINUTES * 60,
                None
            )

        # 4. Invalidate any existing unused codes for this email and purpose
        invalidate_stmt = (
            update(VerificationCode)
            .where(
                VerificationCode.email == clean_email,
                VerificationCode.purpose == purpose,
                VerificationCode.is_used == False
            )
            .values(is_used=True)
        )
        await db.execute(invalidate_stmt)

        # 5. Generate secure 6-digit code
        raw_code = generate_6_digit_code()
        hashed_code = hash_password(raw_code)
        expires_at = datetime.now(timezone.utc) + timedelta(minutes=settings.VERIFICATION_CODE_EXPIRE_MINUTES)

        verif_entry = VerificationCode(
            email=clean_email,
            code_hash=hashed_code,
            purpose=purpose,
            attempts=0,
            max_attempts=settings.MAX_VERIFICATION_ATTEMPTS,
            expires_at=expires_at,
            is_used=False
        )
        db.add(verif_entry)
        await db.commit()

        # 6. Dispatch via SMTP
        sent, err_msg = await EmailService.send_verification_code(clean_email, raw_code, purpose)

        # For development / testing when SMTP is not configured, provide dev_code in response
        dev_code = raw_code if not EmailService.is_configured() else None

        msg = (
            "Verification code sent to your email."
            if sent
            else f"Verification code generated (SMTP notice: {err_msg})"
        )

        return (
            msg,
            settings.RESEND_COOLDOWN_SECONDS,
            settings.VERIFICATION_CODE_EXPIRE_MINUTES * 60,
            dev_code
        )

    @classmethod
    async def verify_code(
        cls,
        db: AsyncSession,
        email: str,
        code: str,
        purpose: str,
        ip_address: Optional[str] = None
    ) -> str:
        """
        Verifies the 6-digit code against Argon2id hash.
        Enforces 5-minute expiry and attempt limit.
        Invalidates code upon success.
        Returns a temporary signed verification token.
        """
        clean_email = email.strip().lower()

        # Find latest code for this email and purpose
        stmt = (
            select(VerificationCode)
            .where(
                VerificationCode.email == clean_email,
                VerificationCode.purpose == purpose,
                VerificationCode.is_used == False
            )
            .order_by(VerificationCode.id.desc())
        )
        result = await db.execute(stmt)
        record = result.scalars().first()

        if not record:
            event = SecurityEvent(
                event_type="INVALID_VERIFICATION_CODE",
                email=clean_email,
                ip_address=ip_address,
                severity="WARNING",
                details="Verification attempted with non-existent or already used code."
            )
            db.add(event)
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid or expired verification code. Please request a new code."
            )

        # Check expiration (5 minutes)
        if record.is_expired():
            record.is_used = True
            event = SecurityEvent(
                event_type="CODE_EXPIRED",
                email=clean_email,
                ip_address=ip_address,
                severity="INFO",
                details="Attempted verification with expired code."
            )
            db.add(event)
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Verification code has expired. Codes are only valid for 5 minutes."
            )

        # Increment attempt counter
        record.attempts += 1

        if record.attempts > record.max_attempts:
            record.is_used = True
            event = SecurityEvent(
                event_type="MAX_ATTEMPTS_EXCEEDED",
                email=clean_email,
                ip_address=ip_address,
                severity="SUSPICIOUS",
                details="Maximum incorrect verification attempts exceeded. Code invalidated."
            )
            db.add(event)
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Maximum verification attempts exceeded. This code has been invalidated. Please request a new code."
            )

        # Constant-time verify code
        is_valid = verify_password(code, record.code_hash)
        if not is_valid:
            remaining = record.max_attempts - record.attempts
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Incorrect verification code. {remaining} attempt(s) remaining."
            )

        # Validated! Invalidate code so it cannot be used again
        record.is_used = True
        await db.commit()

        # Generate a temporary verification token valid for 10 minutes
        token_data = {
            "sub": clean_email,
            "purpose": purpose,
            "verif_id": record.id,
            "type": "email_verification"
        }
        verification_token = create_access_token(token_data, expires_delta=timedelta(minutes=10))
        return verification_token

    @classmethod
    async def create_password_and_register(
        cls,
        db: AsyncSession,
        email: str,
        username: str,
        password: str,
        verification_token: str,
        ip_address: Optional[str] = None
    ) -> Tuple[User, str, str]:
        """
        Creates an account with an Argon2id hashed password after email verification.
        Returns (User, access_token, session_token).
        """
        clean_email = email.strip().lower()

        # Validate verification token
        payload = decode_access_token(verification_token)
        if (
            not payload
            or payload.get("sub") != clean_email
            or payload.get("purpose") != CodePurpose.REGISTRATION.value
            or payload.get("type") != "email_verification"
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Invalid or expired verification session. Please verify your email again."
            )

        # Check if user already exists
        stmt = select(User).where(User.email == clean_email)
        res = await db.execute(stmt)
        if res.scalar_one_or_none():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="An account with this email address is already registered."
            )

        # Check if username is already taken (case-insensitive)
        clean_username = username.strip()
        stmt_u = select(User).where(func.lower(User.username) == clean_username.lower())
        res_u = await db.execute(stmt_u)
        if res_u.scalar_one_or_none():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Username '{clean_username}' is already taken. Please choose another username."
            )

        # Hash password securely with Argon2id
        argon2_hash = hash_password(password)

        # Automatic Founder & Super Admin identification for the original founder account
        is_founder_email = clean_email in [settings.FOUNDER_EMAIL.strip().lower(), "bakirof4@gmail.com"]
        role = UserRole.SUPER_ADMIN.value if is_founder_email else UserRole.USER.value
        is_founder = is_founder_email
        is_verified_badge = is_founder_email

        new_user = User(
            email=clean_email,
            username=clean_username,
            hashed_password=argon2_hash,
            role=role,
            is_founder=is_founder,
            is_verified_badge=is_verified_badge,
            is_active=True,
            email_verified=True,
            last_login_at=datetime.now(timezone.utc)
        )
        db.add(new_user)
        await db.flush()

        # Create session
        session_token = generate_session_token()
        session_expires = datetime.now(timezone.utc) + timedelta(days=settings.SESSION_EXPIRE_DAYS)
        user_session = UserSession(
            session_token=session_token,
            user_id=new_user.id,
            expires_at=session_expires,
            ip_address=ip_address
        )
        db.add(user_session)

        # Audit log
        audit = AuditLog(
            actor_id=new_user.id,
            actor_email=clean_email,
            action="USER_REGISTRATION",
            target_id=new_user.id,
            target_email=clean_email,
            details=f"User registered with email {clean_email}",
            ip_address=ip_address
        )
        db.add(audit)
        await db.commit()
        await db.refresh(new_user)

        # JWT Access Token
        jwt_token = create_access_token({
            "sub": str(new_user.id),
            "email": new_user.email,
            "role": new_user.role,
            "is_founder": new_user.is_founder,
            "session_token": session_token
        })

        return new_user, jwt_token, session_token

    @classmethod
    async def login(
        cls,
        db: AsyncSession,
        email: str,
        password: str,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None
    ) -> Tuple[User, str, str]:
        """
        Authenticates user with registered email and ZeroChat AI password.
        Enforces login attempt limits, Argon2id check, and session creation.
        """
        clean_email = email.strip().lower()

        # Check failed login rate limit (5 attempts per 15 min)
        rate_key = f"login_fail:{clean_email}"
        is_allowed, _ = login_attempt_limiter.check_rate_limit(
            rate_key, settings.MAX_LOGIN_ATTEMPTS_PER_15MIN, 900
        )
        if not is_allowed:
            event = SecurityEvent(
                event_type="ACCOUNT_LOCKED_FAILED_LOGINS",
                email=clean_email,
                ip_address=ip_address,
                severity="HIGH",
                details="Account temporarily locked due to repeated failed login attempts."
            )
            db.add(event)
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many failed login attempts. Account temporarily locked for 15 minutes."
            )

        # Fetch user
        stmt = select(User).where(User.email == clean_email)
        res = await db.execute(stmt)
        user = res.scalar_one_or_none()

        if not user or not verify_password(password, user.hashed_password):
            login_attempt_limiter.record_request(rate_key)
            event = SecurityEvent(
                event_type="FAILED_LOGIN",
                email=clean_email,
                ip_address=ip_address,
                severity="WARNING",
                details="Invalid email or password credentials provided."
            )
            db.add(event)
            await db.commit()
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email or password. Please verify your credentials."
            )

        if not user.is_active:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="This account has been deactivated. Please contact support."
            )

        # Successful login: reset rate limiter
        login_attempt_limiter.reset(rate_key)
        user.last_login_at = datetime.now(timezone.utc)

        # Automatic Founder & Super Admin identification for the original founder account
        if clean_email in [settings.FOUNDER_EMAIL.strip().lower(), "bakirof4@gmail.com"]:
            if not user.is_founder or user.role != UserRole.SUPER_ADMIN.value or not user.is_verified_badge:
                user.is_founder = True
                user.role = UserRole.SUPER_ADMIN.value
                user.is_verified_badge = True

        # Issue session
        session_token = generate_session_token()
        session_expires = datetime.now(timezone.utc) + timedelta(days=settings.SESSION_EXPIRE_DAYS)
        user_session = UserSession(
            session_token=session_token,
            user_id=user.id,
            expires_at=session_expires,
            ip_address=ip_address,
            user_agent=user_agent
        )
        db.add(user_session)

        # Log security event
        event = SecurityEvent(
            event_type="LOGIN_SUCCESS",
            email=clean_email,
            ip_address=ip_address,
            severity="INFO",
            details=f"User {user.email} (Role: {user.role}) logged in successfully."
        )
        db.add(event)
        await db.commit()
        await db.refresh(user)

        jwt_token = create_access_token({
            "sub": str(user.id),
            "email": user.email,
            "role": user.role,
            "is_founder": user.is_founder,
            "session_token": session_token
        })

        return user, jwt_token, session_token

    @classmethod
    async def google_sign_in(
        cls,
        db: AsyncSession,
        id_token_str: str,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None
    ) -> Tuple[User, str, str]:
        """
        Authenticates or registers a user via Google Identity (OAuth 2.0 / OpenID Connect).
        - Verifies signature, issuer, audience, and expiry via Google JWKs.
        - Retrieves email, google_id, name, and profile picture.
        - Preserves existing accounts and links them smoothly.
        - Strictly enforces founder & SUPER_ADMIN rights for bakirof4@gmail.com
          ONLY upon verified Google email ownership.
        """
        claims = GoogleAuthService.verify_id_token(id_token_str)
        google_id = claims["google_id"]
        email = claims["email"]
        full_name = claims.get("name")
        avatar_url = claims.get("picture")
        email_verified = claims.get("email_verified", True)

        # 1. Look up existing user by google_id or email
        stmt = select(User).where((User.google_id == google_id) | (User.email == email))
        res = await db.execute(stmt)
        user = res.scalar_one_or_none()

        if user:
            # Account linking & profile updates
            if not user.google_id:
                user.google_id = google_id
            if full_name and not user.full_name:
                user.full_name = full_name
            if avatar_url and not user.avatar_url:
                user.avatar_url = avatar_url
            user.email_verified = True
            user.last_login_at = datetime.now(timezone.utc)
        else:
            # Create new user: generate a clean unique username
            base_name = email.split("@")[0]
            clean_name = re.sub(r"[^a-zA-Z0-9_]", "", base_name)[:20]
            if len(clean_name) < 3:
                clean_name = f"user_{random.randint(1000, 9999)}"

            candidate_username = clean_name
            attempt = 1
            while True:
                existing_u = await cls.check_username_available(db, candidate_username)
                if existing_u:
                    break
                candidate_username = f"{clean_name[:15]}_{random.randint(100, 9999)}"
                attempt += 1
                if attempt > 10:
                    candidate_username = f"user_{random.randint(100000, 999999)}"
                    break

            user = User(
                email=email,
                username=candidate_username,
                google_id=google_id,
                full_name=full_name,
                avatar_url=avatar_url,
                hashed_password=None,
                role=UserRole.USER.value,
                is_founder=False,
                is_verified_badge=False,
                is_active=True,
                email_verified=True,
                last_login_at=datetime.now(timezone.utc)
            )
            db.add(user)
            await db.flush()

        # Enforce Founder & Super Admin privileges for bakirof4@gmail.com
        # ONLY after email ownership is proven by Google token
        is_founder_email = email in [settings.FOUNDER_EMAIL.strip().lower(), "bakirof4@gmail.com"]
        if is_founder_email and email_verified:
            user.is_founder = True
            user.role = UserRole.SUPER_ADMIN.value
            user.is_verified_badge = True

        if not user.is_active:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="This account has been deactivated. Please contact support."
            )

        # Issue session
        session_token = generate_session_token()
        session_expires = datetime.now(timezone.utc) + timedelta(days=settings.SESSION_EXPIRE_DAYS)
        user_session = UserSession(
            session_token=session_token,
            user_id=user.id,
            expires_at=session_expires,
            ip_address=ip_address,
            user_agent=user_agent
        )
        db.add(user_session)

        # Audit and security event logs
        audit = AuditLog(
            actor_id=user.id,
            actor_email=email,
            action="GOOGLE_LOGIN_SUCCESS",
            target_id=user.id,
            target_email=email,
            details=f"User authenticated via Google Identity (Google ID: {google_id}).",
            ip_address=ip_address
        )
        db.add(audit)

        event = SecurityEvent(
            event_type="GOOGLE_AUTH_SUCCESS",
            email=email,
            ip_address=ip_address,
            severity="INFO",
            details=f"Google OAuth 2.0 login verified for {user.email} (Role: {user.role})."
        )
        db.add(event)

        await db.commit()
        await db.refresh(user)

        jwt_token = create_access_token({
            "sub": str(user.id),
            "email": user.email,
            "role": user.role,
            "is_founder": user.is_founder,
            "session_token": session_token
        })

        return user, jwt_token, session_token

    @classmethod
    async def passwordless_login(
        cls,
        db: AsyncSession,
        email: str,
        verification_token: str,
        ip_address: Optional[str] = None,
        user_agent: Optional[str] = None
    ) -> Tuple[User, str, str]:
        """
        Authenticates or provisions a user via verified 6-digit one-time email code token.
        Provides a zero-password alternative for devices lacking Google Play Services
        (such as Huawei devices, custom ROMs, or bare emulators).
        """
        clean_email = email.strip().lower()

        # Validate verification token
        payload = decode_access_token(verification_token)
        if (
            not payload
            or payload.get("sub") != clean_email
            or payload.get("type") != "email_verification"
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Invalid or expired verification session. Please request a new verification code."
            )

        # Look up existing user
        stmt = select(User).where(User.email == clean_email)
        res = await db.execute(stmt)
        user = res.scalar_one_or_none()

        if user:
            user.email_verified = True
            user.last_login_at = datetime.now(timezone.utc)
        else:
            # Generate a unique username for first-time passwordless sign-in
            base_name = clean_email.split("@")[0]
            clean_name = re.sub(r"[^a-zA-Z0-9_]", "", base_name)[:20]
            if len(clean_name) < 3:
                clean_name = f"user_{random.randint(1000, 9999)}"

            candidate_username = clean_name
            attempt = 1
            while True:
                existing_u = await cls.check_username_available(db, candidate_username)
                if existing_u:
                    break
                candidate_username = f"{clean_name[:15]}_{random.randint(100, 9999)}"
                attempt += 1
                if attempt > 10:
                    candidate_username = f"user_{random.randint(100000, 999999)}"
                    break

            user = User(
                email=clean_email,
                username=candidate_username,
                hashed_password=None,
                role=UserRole.USER.value,
                is_founder=False,
                is_verified_badge=False,
                is_active=True,
                email_verified=True,
                last_login_at=datetime.now(timezone.utc)
            )
            db.add(user)
            await db.flush()

        # Enforce Founder & Super Admin privileges for bakirof4@gmail.com
        is_founder_email = clean_email in [settings.FOUNDER_EMAIL.strip().lower(), "bakirof4@gmail.com"]
        if is_founder_email:
            user.is_founder = True
            user.role = UserRole.SUPER_ADMIN.value
            user.is_verified_badge = True

        if not user.is_active:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="This account has been deactivated. Please contact support."
            )

        # Issue session
        session_token = generate_session_token()
        session_expires = datetime.now(timezone.utc) + timedelta(days=settings.SESSION_EXPIRE_DAYS)
        user_session = UserSession(
            session_token=session_token,
            user_id=user.id,
            expires_at=session_expires,
            ip_address=ip_address,
            user_agent=user_agent
        )
        db.add(user_session)

        # Audit and security event
        audit = AuditLog(
            actor_id=user.id,
            actor_email=clean_email,
            action="PASSWORDLESS_LOGIN_SUCCESS",
            target_id=user.id,
            target_email=clean_email,
            details=f"User authenticated via verified 6-digit email OTP (zero passwords required).",
            ip_address=ip_address
        )
        db.add(audit)

        event = SecurityEvent(
            event_type="PASSWORDLESS_LOGIN_SUCCESS",
            email=clean_email,
            ip_address=ip_address,
            severity="INFO",
            details=f"Passwordless email OTP login successful for {user.email} (Role: {user.role})."
        )
        db.add(event)

        await db.commit()
        await db.refresh(user)

        jwt_token = create_access_token({
            "sub": str(user.id),
            "email": user.email,
            "role": user.role,
            "is_founder": user.is_founder,
            "session_token": session_token
        })

        return user, jwt_token, session_token

    @classmethod
    async def reset_password(
        cls,
        db: AsyncSession,
        email: str,
        new_password: str,
        verification_token: str,
        ip_address: Optional[str] = None
    ) -> None:
        """
        Resets user password after successful verification token validation.
        Invalidates all existing sessions for security.
        """
        clean_email = email.strip().lower()

        # Validate verification token
        payload = decode_access_token(verification_token)
        if (
            not payload
            or payload.get("sub") != clean_email
            or payload.get("purpose") != CodePurpose.PASSWORD_RESET.value
            or payload.get("type") != "email_verification"
        ):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Invalid or expired verification session for password reset."
            )

        stmt = select(User).where(User.email == clean_email)
        res = await db.execute(stmt)
        user = res.scalar_one_or_none()
        if not user:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="User account not found."
            )

        # Hash new password
        user.hashed_password = hash_password(new_password)

        # Invalidate all existing sessions
        revoke_stmt = (
            update(UserSession)
            .where(UserSession.user_id == user.id)
            .values(is_revoked=True)
        )
        await db.execute(revoke_stmt)

        # Audit log
        audit = AuditLog(
            actor_id=user.id,
            actor_email=clean_email,
            action="PASSWORD_RESET",
            target_id=user.id,
            target_email=clean_email,
            details="Password successfully reset via email verification.",
            ip_address=ip_address
        )
        db.add(audit)
        await db.commit()

    @classmethod
    async def logout(cls, db: AsyncSession, session_token: Optional[str]) -> None:
        """Revokes the active user session."""
        if not session_token:
            return
        stmt = (
            update(UserSession)
            .where(UserSession.session_token == session_token)
            .values(is_revoked=True)
        )
        await db.execute(stmt)
        await db.commit()

    @classmethod
    async def check_username_available(cls, db: AsyncSession, username: str) -> bool:
        """Checks if a username is available (case-insensitive)."""
        clean_username = username.strip().lower()
        stmt = select(User).where(func.lower(User.username) == clean_username)
        res = await db.execute(stmt)
        return res.scalar_one_or_none() is None


