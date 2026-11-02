from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, EmailStr


class UserBase(BaseModel):
    email: EmailStr
    username: str


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    username: str
    role: str
    is_founder: bool
    is_verified_badge: bool
    is_active: bool
    email_verified: bool
    created_at: datetime
    last_login_at: Optional[datetime] = None
    bio: Optional[str] = None
    avatar_url: Optional[str] = None
    google_id: Optional[str] = None
    full_name: Optional[str] = None


class UserProfileUpdate(BaseModel):
    username: Optional[str] = None

