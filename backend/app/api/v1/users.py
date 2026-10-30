import io
import re
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from pydantic import BaseModel
import qrcode
from sqlalchemy import delete, func, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.deps import get_current_user
from app.db.session import get_db
from app.models.block_list import UserBlock
from app.models.qr_contact import QrContact
from app.models.user import User
from app.schemas.user import UserResponse

router = APIRouter(prefix="/users", tags=["Users & QR"])


class QrCodeResponse(BaseModel):
    user_id: int
    username: str
    email: str
    is_founder: bool
    is_verified_badge: bool
    qr_payload: str
    deep_link: str


class QrScanRequest(BaseModel):
    qr_content: str


class QrScanResponse(BaseModel):
    user: UserResponse
    is_blocked_by_me: bool


class SaveContactRequest(BaseModel):
    contact_user_id: int
    qr_payload: Optional[str] = None
    nickname: Optional[str] = None


class QrContactResponse(BaseModel):
    id: int
    user_id: int
    contact_user: UserResponse
    qr_payload: Optional[str] = None
    nickname: Optional[str] = None


@router.get("/me/qr", response_model=QrCodeResponse)
async def get_my_qr_code(
    current_user: User = Depends(get_current_user)
):
    """
    Returns the authenticated user's QR payload and deep link.
    Format: zerochat://user?u={username}&uid={user_id}
    """
    qr_payload = f"zerochat://user?u={current_user.username}&uid={current_user.id}"
    return QrCodeResponse(
        user_id=current_user.id,
        username=current_user.username,
        email=current_user.email,
        is_founder=current_user.is_founder,
        is_verified_badge=current_user.is_verified_badge,
        qr_payload=qr_payload,
        deep_link=qr_payload
    )


@router.get("/{username}/qr.png")
async def get_user_qr_image(
    username: str,
    db: AsyncSession = Depends(get_db)
):
    """
    Renders and streams a PNG QR code image for the specified user.
    """
    stmt = select(User).where(func.lower(User.username) == username.strip().lower())
    res = await db.execute(stmt)
    user = res.scalar_one_or_none()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User '{username}' was not found."
        )

    qr_payload = f"zerochat://user?u={user.username}&uid={user.id}"

    qr = qrcode.QRCode(
        version=1,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=10,
        border=4,
    )
    qr.add_data(qr_payload)
    qr.make(fit=True)

    img = qr.make_image(fill_color="black", back_color="white")
    buffer = io.BytesIO()
    img.save(buffer, format="PNG")
    buffer.seek(0)

    return Response(content=buffer.getvalue(), media_type="image/png")


@router.get("/lookup", response_model=UserResponse)
async def lookup_user(
    username: str = Query(..., min_length=1),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Looks up a user's public profile by username.
    """
    clean_username = username.strip().lower()
    stmt = select(User).where(func.lower(User.username) == clean_username)
    res = await db.execute(stmt)
    target = res.scalar_one_or_none()

    if not target:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User '{username}' was not found."
        )

    return UserResponse.model_validate(target)


@router.post("/qr-scan", response_model=QrScanResponse)
async def scan_qr_code(
    payload: QrScanRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Validates a scanned QR code payload, resolves the user,
    and checks if either user has blocked the other.
    """
    qr_text = payload.qr_content.strip()

    target_user: Optional[User] = None

    # Format 1: zerochat://user?u={username}&uid={user_id}
    match = re.search(r"zerochat://user\?u=([^&]+)(?:&uid=(\d+))?", qr_text)
    if match:
        extracted_username = match.group(1)
        stmt = select(User).where(func.lower(User.username) == extracted_username.lower())
        res = await db.execute(stmt)
        target_user = res.scalar_one_or_none()
    else:
        # Format 2: raw username or @username
        raw_u = qr_text.lstrip("@")
        stmt = select(User).where(func.lower(User.username) == raw_u.lower())
        res = await db.execute(stmt)
        target_user = res.scalar_one_or_none()

    if not target_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Invalid QR code: no corresponding ZeroChat AI user was found."
        )

    # Check if target user has blocked current user
    stmt_blocked_by_them = select(UserBlock).where(
        UserBlock.blocker_id == target_user.id,
        UserBlock.blocked_id == current_user.id
    )
    if (await db.execute(stmt_blocked_by_them)).scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You cannot interact with this user because they have blocked you."
        )

    # Check if current user has blocked target user
    stmt_blocked_by_me = select(UserBlock).where(
        UserBlock.blocker_id == current_user.id,
        UserBlock.blocked_id == target_user.id
    )
    is_blocked_by_me = (await db.execute(stmt_blocked_by_me)).scalar_one_or_none() is not None

    return QrScanResponse(
        user=UserResponse.model_validate(target_user),
        is_blocked_by_me=is_blocked_by_me
    )


@router.post("/{user_id}/block", response_model=dict)
async def block_user(
    user_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Blocks a user, preventing messaging and profile interaction.
    """
    if user_id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot block your own account."
        )

    # Check target exists
    target = await db.get(User, user_id)
    if not target:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Target user not found."
        )

    # Check if already blocked
    stmt = select(UserBlock).where(
        UserBlock.blocker_id == current_user.id,
        UserBlock.blocked_id == user_id
    )
    existing = (await db.execute(stmt)).scalar_one_or_none()
    if not existing:
        block = UserBlock(blocker_id=current_user.id, blocked_id=user_id)
        db.add(block)
        await db.commit()

    return {"message": f"User '{target.username}' has been blocked."}


@router.post("/{user_id}/unblock", response_model=dict)
async def unblock_user(
    user_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Unblocks a previously blocked user.
    """
    stmt = delete(UserBlock).where(
        UserBlock.blocker_id == current_user.id,
        UserBlock.blocked_id == user_id
    )
    await db.execute(stmt)
    await db.commit()
    return {"message": f"User ID {user_id} unblocked."}


@router.get("/blocked", response_model=List[UserResponse])
async def list_blocked_users(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Returns list of users blocked by the authenticated user.
    """
    stmt = (
        select(User)
        .join(UserBlock, UserBlock.blocked_id == User.id)
        .where(UserBlock.blocker_id == current_user.id)
    )
    res = await db.execute(stmt)
    blocked_users = res.scalars().all()
    return [UserResponse.model_validate(u) for u in blocked_users]


@router.post("/contacts", response_model=QrContactResponse)
async def save_qr_contact(
    payload: SaveContactRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Saves or updates a contact discovered through QR code scanning.
    """
    if payload.contact_user_id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot add yourself as a contact."
        )

    contact_user = await db.get(User, payload.contact_user_id)
    if not contact_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Contact user not found."
        )

    stmt = select(QrContact).where(
        QrContact.user_id == current_user.id,
        QrContact.contact_user_id == payload.contact_user_id
    )
    existing = (await db.execute(stmt)).scalar_one_or_none()

    if existing:
        if payload.nickname is not None:
            existing.nickname = payload.nickname
        if payload.qr_payload is not None:
            existing.qr_payload = payload.qr_payload
        await db.commit()
        await db.refresh(existing)
        contact_record = existing
    else:
        contact_record = QrContact(
            user_id=current_user.id,
            contact_user_id=payload.contact_user_id,
            qr_payload=payload.qr_payload,
            nickname=payload.nickname
        )
        db.add(contact_record)
        await db.commit()
        await db.refresh(contact_record)

    return QrContactResponse(
        id=contact_record.id,
        user_id=current_user.id,
        contact_user=UserResponse.model_validate(contact_user),
        qr_payload=contact_record.qr_payload,
        nickname=contact_record.nickname
    )


@router.get("/contacts", response_model=List[QrContactResponse])
async def list_qr_contacts(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Lists all saved QR contacts for the authenticated user.
    """
    stmt = (
        select(QrContact, User)
        .join(User, QrContact.contact_user_id == User.id)
        .where(QrContact.user_id == current_user.id)
        .order_by(QrContact.created_at.desc())
    )
    res = await db.execute(stmt)
    rows = res.all()

    contacts = []
    for c, u in rows:
        contacts.append(
            QrContactResponse(
                id=c.id,
                user_id=c.user_id,
                contact_user=UserResponse.model_validate(u),
                qr_payload=c.qr_payload,
                nickname=c.nickname
            )
        )
    return contacts


@router.delete("/contacts/{contact_user_id}", response_model=dict)
async def delete_qr_contact(
    contact_user_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Removes a saved QR contact.
    """
    stmt = delete(QrContact).where(
        QrContact.user_id == current_user.id,
        QrContact.contact_user_id == contact_user_id
    )
    await db.execute(stmt)
    await db.commit()
    return {"message": "Contact removed successfully."}
