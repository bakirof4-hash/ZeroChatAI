import re
from typing import Optional
from pydantic import BaseModel, EmailStr, Field, field_validator
from app.schemas.user import UserResponse


class SendCodeRequest(BaseModel):
    email: EmailStr
    purpose: str = Field(default="REGISTRATION", description="REGISTRATION or PASSWORD_RESET")

    @field_validator("purpose")
    @classmethod
    def validate_purpose(cls, v: str) -> str:
        upper = v.upper()
        if upper not in ["REGISTRATION", "PASSWORD_RESET"]:
            raise ValueError("Purpose must be REGISTRATION or PASSWORD_RESET")
        return upper


class SendCodeResponse(BaseModel):
    message: str
    cooldown_seconds: int
    expires_in_seconds: int
    # Note: In development mode if SMTP is disabled, test_code may be populated for convenience
    dev_code: Optional[str] = None


class VerifyCodeRequest(BaseModel):
    email: EmailStr
    code: str = Field(..., min_length=6, max_length=6, description="6-digit verification code")
    purpose: str = Field(default="REGISTRATION")

    @field_validator("code")
    @classmethod
    def validate_code(cls, v: str) -> str:
        v = v.strip()
        if not re.match(r"^\d{6}$", v):
            raise ValueError("Verification code must be exactly 6 digits")
        return v


class VerifyCodeResponse(BaseModel):
    message: str
    verification_token: str
    email: str


class CreatePasswordRequest(BaseModel):
    verification_token: str
    email: EmailStr
    username: str = Field(..., min_length=2, max_length=50)
    password: str = Field(..., min_length=8, max_length=128)
    confirm_password: str

    @field_validator("password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("Password must be at least 8 characters long")
        if not any(c.isupper() for c in v):
            raise ValueError("Password must contain at least one uppercase letter")
        if not any(c.islower() for c in v):
            raise ValueError("Password must contain at least one lowercase letter")
        if not any(c.isdigit() for c in v):
            raise ValueError("Password must contain at least one numeric digit")
        return v


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(..., min_length=1)


class AuthTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    session_token: str
    user: UserResponse


class PasswordResetRequest(BaseModel):
    verification_token: str
    email: EmailStr
    new_password: str = Field(..., min_length=8, max_length=128)
    confirm_password: str

    @field_validator("new_password")
    @classmethod
    def validate_password_strength(cls, v: str) -> str:
        if len(v) < 8:
            raise ValueError("Password must be at least 8 characters long")
        if not any(c.isupper() for c in v):
            raise ValueError("Password must contain at least one uppercase letter")
        if not any(c.islower() for c in v):
            raise ValueError("Password must contain at least one lowercase letter")
        if not any(c.isdigit() for c in v):
            raise ValueError("Password must contain at least one numeric digit")
        return v


class LogoutRequest(BaseModel):
    session_token: Optional[str] = None

