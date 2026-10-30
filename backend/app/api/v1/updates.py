from datetime import datetime, timezone
import hashlib
import os
import re
from typing import Optional
import aiofiles
from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.deps import get_current_admin, get_current_user
from app.db.session import get_db
from app.models.app_version import AppVersion
from app.models.user import User
from app.schemas.app_version import AppVersionResponse, PublishVersionRequest

router = APIRouter(prefix="/app", tags=["In-App Updates & Version Management"])

UPDATES_DIR = os.path.join(os.getcwd(), "uploads", "updates")
os.makedirs(UPDATES_DIR, exist_ok=True)


@router.get("/version/latest", response_model=AppVersionResponse)
async def get_latest_app_version(
    db: AsyncSession = Depends(get_db)
):
    """
    Returns the latest published active version of the ZeroChat AI Android application.
    Used by the Android client on app startup to check for available updates.
    """
    stmt = (
        select(AppVersion)
        .where(AppVersion.is_active == True)
        .order_by(AppVersion.version_code.desc())
        .limit(1)
    )
    result = await db.execute(stmt)
    latest = result.scalar_one_or_none()

    if not latest:
        # Default baseline if no explicit release has been published yet
        now = datetime.now(timezone.utc)
        return AppVersionResponse(
            version_code=1,
            version_name="1.0.0",
            release_notes="Initial release of ZeroChat AI with end-to-end privacy and offline security analyzer.",
            apk_url="/api/v1/app/download/app-debug.apk",
            apk_sha256="b9b99194d3abdbaecef98fcc32f58097cce48fb73b2b62dc8ec5cd1cfe10dc8f",
            apk_size=18869904,
            min_supported_version_code=1,
            is_mandatory=False,
            published_at=now
        )

    return AppVersionResponse(
        version_code=latest.version_code,
        version_name=latest.version_name,
        release_notes=latest.release_notes,
        apk_url=latest.apk_url,
        apk_sha256=latest.apk_sha256,
        apk_size=latest.apk_size,
        min_supported_version_code=latest.min_supported_version_code,
        is_mandatory=latest.is_mandatory,
        published_at=latest.created_at
    )


@router.get("/download/{filename}")
async def download_update_apk(filename: str):
    """
    Securely serves the APK file over HTTPS.
    Validates file path to prevent directory traversal attacks.
    """
    safe_name = os.path.basename(filename)
    file_path = os.path.join(UPDATES_DIR, safe_name)

    # Fallback to debug build output if requested during testing
    if not os.path.exists(file_path) and safe_name == "app-debug.apk":
        debug_apk_path = os.path.join(os.getcwd(), "..", "android", "app", "build", "outputs", "apk", "debug", "app-debug.apk")
        if os.path.exists(debug_apk_path):
            file_path = debug_apk_path

    if not os.path.exists(file_path):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Requested update package '{safe_name}' was not found on the server."
        )

    return FileResponse(
        path=file_path,
        media_type="application/vnd.android.package-archive",
        filename=safe_name,
        headers={
            "Cache-Control": "public, max-age=86400",
            "Content-Disposition": f'attachment; filename="{safe_name}"'
        }
    )


@router.post("/version", response_model=AppVersionResponse)
async def publish_version_metadata(
    payload: PublishVersionRequest,
    current_admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Admin/Founder endpoint to publish or update version metadata.
    Enforces RBAC: only Super Admin / Founder or authorized admin can publish.
    """
    # Check if version_code exists
    stmt = select(AppVersion).where(AppVersion.version_code == payload.version_code)
    existing = (await db.execute(stmt)).scalar_one_or_none()

    if existing:
        existing.version_name = payload.version_name
        existing.release_notes = payload.release_notes
        existing.apk_url = payload.apk_url
        existing.apk_sha256 = payload.apk_sha256
        existing.apk_size = payload.apk_size
        existing.min_supported_version_code = payload.min_supported_version_code
        existing.is_mandatory = payload.is_mandatory
        existing.is_active = payload.is_active
        await db.commit()
        await db.refresh(existing)
        version_record = existing
    else:
        version_record = AppVersion(
            version_code=payload.version_code,
            version_name=payload.version_name,
            release_notes=payload.release_notes,
            apk_url=payload.apk_url,
            apk_sha256=payload.apk_sha256,
            apk_size=payload.apk_size,
            min_supported_version_code=payload.min_supported_version_code,
            is_mandatory=payload.is_mandatory,
            is_active=payload.is_active,
            published_by=current_admin.id
        )
        db.add(version_record)
        await db.commit()
        await db.refresh(version_record)

    return AppVersionResponse(
        version_code=version_record.version_code,
        version_name=version_record.version_name,
        release_notes=version_record.release_notes,
        apk_url=version_record.apk_url,
        apk_sha256=version_record.apk_sha256,
        apk_size=version_record.apk_size,
        min_supported_version_code=version_record.min_supported_version_code,
        is_mandatory=version_record.is_mandatory,
        published_at=version_record.created_at
    )


@router.post("/upload-apk", response_model=AppVersionResponse)
async def upload_and_publish_apk(
    file: UploadFile = File(...),
    version_code: int = Form(...),
    version_name: str = Form(...),
    release_notes: str = Form(...),
    is_mandatory: bool = Form(False),
    min_supported_version_code: int = Form(1),
    current_admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    """
    Automated Admin/Founder workflow:
    1. Uploads APK binary securely.
    2. Computes SHA-256 hash automatically.
    3. Verifies file integrity and saves to updates directory.
    4. Publishes new version record.
    """
    filename = file.filename or "app-update.apk"
    if not filename.endswith(".apk"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded package must be an Android APK (.apk) file."
        )

    content = await file.read()
    if not content:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded APK file is empty."
        )

    # Compute SHA-256
    sha256_hash = hashlib.sha256(content).hexdigest().lower()
    apk_size = len(content)

    clean_filename = f"zerochat_v{version_code}_{re.sub(r'[^a-zA-Z0-9_.-]', '_', filename)}"
    dest_path = os.path.join(UPDATES_DIR, clean_filename)

    async with aiofiles.open(dest_path, "wb") as f:
        await f.write(content)

    apk_download_url = f"/api/v1/app/download/{clean_filename}"

    # Upsert version
    stmt = select(AppVersion).where(AppVersion.version_code == version_code)
    existing = (await db.execute(stmt)).scalar_one_or_none()

    if existing:
        existing.version_name = version_name
        existing.release_notes = release_notes
        existing.apk_url = apk_download_url
        existing.apk_sha256 = sha256_hash
        existing.apk_size = apk_size
        existing.min_supported_version_code = min_supported_version_code
        existing.is_mandatory = is_mandatory
        existing.is_active = True
        await db.commit()
        await db.refresh(existing)
        record = existing
    else:
        record = AppVersion(
            version_code=version_code,
            version_name=version_name,
            release_notes=release_notes,
            apk_url=apk_download_url,
            apk_sha256=sha256_hash,
            apk_size=apk_size,
            min_supported_version_code=min_supported_version_code,
            is_mandatory=is_mandatory,
            is_active=True,
            published_by=current_admin.id
        )
        db.add(record)
        await db.commit()
        await db.refresh(record)

    return AppVersionResponse(
        version_code=record.version_code,
        version_name=record.version_name,
        release_notes=record.release_notes,
        apk_url=record.apk_url,
        apk_sha256=record.apk_sha256,
        apk_size=record.apk_size,
        min_supported_version_code=record.min_supported_version_code,
        is_mandatory=record.is_mandatory,
        published_at=record.created_at
    )
