import re
from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.deps import get_client_ip, get_current_user
from app.db.session import get_db
from app.models.user import User
from app.schemas.auth import (
    AuthTokenResponse,
    CreatePasswordRequest,
    LoginRequest,
    LogoutRequest,
    PasswordResetRequest,
    SendCodeRequest,
    SendCodeResponse,
    VerifyCodeRequest,
    VerifyCodeResponse,
)
from app.schemas.user import UserResponse
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["Authentication"])


@router.post("/send-code", response_model=SendCodeResponse)
async def send_verification_code(
    payload: SendCodeRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Step 1: Request a 6-digit one-time verification code.
    - Sends code to user's email via Gmail SMTP / configured SMTP service.
    - Enforces 60-second cooldown and hourly rate limits.
    - Preserves non-disclosure of email registration status.
    - Never requires or stores user's Gmail password.
    """
    ip = get_client_ip(request)
    msg, cooldown, expires_in, dev_code = await AuthService.request_verification_code(
        db=db,
        email=payload.email,
        purpose=payload.purpose,
        ip_address=ip
    )
    return SendCodeResponse(
        message=msg,
        cooldown_seconds=cooldown,
        expires_in_seconds=expires_in,
        dev_code=dev_code
    )


@router.post("/verify-code", response_model=VerifyCodeResponse)
async def verify_code(
    payload: VerifyCodeRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Step 2: Verify the 6-digit one-time verification code.
    - Verifies against Argon2id hash.
    - Enforces 5-minute expiry.
    - Limits incorrect attempts (max 5) before invalidating code.
    - Invalidates code immediately upon success.
    - Returns a signed, short-lived verification token.
    """
    ip = get_client_ip(request)
    verification_token = await AuthService.verify_code(
        db=db,
        email=payload.email,
        code=payload.code,
        purpose=payload.purpose,
        ip_address=ip
    )
    return VerifyCodeResponse(
        message="Code successfully verified.",
        verification_token=verification_token,
        email=str(payload.email)
    )


@router.post("/create-password", response_model=AuthTokenResponse, status_code=status.HTTP_201_CREATED)
async def create_password_and_register(
    payload: CreatePasswordRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Step 3: Create account password after successful email verification.
    - Verifies the temporary verification token.
    - Enforces password strength rules.
    - Hashes password with Argon2id.
    - Registers account and issues secure session and JWT token.
    """
    if payload.password != payload.confirm_password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Password and confirmation do not match."
        )

    ip = get_client_ip(request)
    user, jwt_token, session_token = await AuthService.create_password_and_register(
        db=db,
        email=payload.email,
        username=payload.username,
        password=payload.password,
        verification_token=payload.verification_token,
        ip_address=ip
    )

    return AuthTokenResponse(
        access_token=jwt_token,
        token_type="bearer",
        session_token=session_token,
        user=UserResponse.model_validate(user)
    )


@router.get("/check-username", response_model=dict)
async def check_username(
    username: str = Query(..., min_length=3, max_length=30),
    db: AsyncSession = Depends(get_db)
):
    """
    Validates username format and checks uniqueness on the server.
    """
    clean_username = username.strip()
    if not re.match(r"^[a-zA-Z0-9_]{3,30}$", clean_username):
        return {
            "username": clean_username,
            "available": False,
            "message": "Username must be 3-30 characters long and contain only letters, numbers, and underscores."
        }

    available = await AuthService.check_username_available(db, clean_username)
    return {
        "username": clean_username,
        "available": available,
        "message": "Username is available." if available else "Username is already taken."
    }


@router.post("/login", response_model=AuthTokenResponse)
async def login(
    payload: LoginRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Authenticates user with registered email and ZeroChat AI password.
    - Enforces failed login rate limits.
    - Verifies password with Argon2id.
    - Issues authenticated session and JWT access token.
    """
    ip = get_client_ip(request)
    user_agent = request.headers.get("User-Agent")

    user, jwt_token, session_token = await AuthService.login(
        db=db,
        email=payload.email,
        password=payload.password,
        ip_address=ip,
        user_agent=user_agent
    )

    return AuthTokenResponse(
        access_token=jwt_token,
        token_type="bearer",
        session_token=session_token,
        user=UserResponse.model_validate(user)
    )


@router.post("/password-reset/confirm", response_model=dict)
async def confirm_password_reset(
    payload: PasswordResetRequest,
    request: Request,
    db: AsyncSession = Depends(get_db)
):
    """
    Resets account password after verification code is validated.
    - Requires verification token obtained from `/auth/verify-code` with purpose PASSWORD_RESET.
    - Hashes new password with Argon2id.
    - Revokes all existing user sessions.
    """
    if payload.new_password != payload.confirm_password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Passwords do not match."
        )

    ip = get_client_ip(request)
    await AuthService.reset_password(
        db=db,
        email=payload.email,
        new_password=payload.new_password,
        verification_token=payload.verification_token,
        ip_address=ip
    )
    return {"message": "Password successfully reset. You can now log in with your new password."}


@router.post("/logout", response_model=dict)
async def logout(
    payload: LogoutRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Revokes the current user session."""
    await AuthService.logout(db, payload.session_token)
    return {"message": "Successfully logged out."}


@router.get("/me", response_model=UserResponse)
async def get_current_user_profile(
    current_user: User = Depends(get_current_user)
):
    """Returns the authenticated user's profile and roles."""
    return UserResponse.model_validate(current_user)

