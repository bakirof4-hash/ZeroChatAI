from datetime import datetime
from typing import Optional
from pydantic import BaseModel, ConfigDict, Field


class AppVersionResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    version_code: int
    version_name: str
    release_notes: str
    apk_url: str
    apk_sha256: str
    apk_size: int
    min_supported_version_code: int = 1
    is_mandatory: bool = False
    published_at: datetime


class PublishVersionRequest(BaseModel):
    version_code: int = Field(..., ge=1)
    version_name: str = Field(..., min_length=1, max_length=50)
    release_notes: str = Field(..., min_length=1)
    apk_url: str = Field(..., min_length=1)
    apk_sha256: str = Field(..., min_length=64, max_length=64)
    apk_size: int = Field(..., ge=1)
    min_supported_version_code: int = 1
    is_mandatory: bool = False
    is_active: bool = True
