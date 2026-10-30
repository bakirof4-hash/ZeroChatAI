from datetime import datetime, timezone
import os
import re
import time
from typing import List, Optional
import aiofiles
from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile, status
from pydantic import BaseModel
from sqlalchemy import func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.deps import get_current_user
from app.db.session import get_db
from app.models.block_list import UserBlock
from app.models.message import ChatMessage
from app.models.user import User
from app.schemas.user import UserResponse
from app.services.file_analyzer import FileSecurityAnalyzer

router = APIRouter(prefix="/chat", tags=["Chat & Messaging"])

UPLOAD_DIR = os.path.join(os.getcwd(), "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)


class ChatMessageResponse(BaseModel):
    id: int
    sender_id: int
    sender_username: str
    sender_email: str
    recipient_id: Optional[int] = None
    is_founder: bool
    is_verified_badge: bool
    content: str
    attachment_url: Optional[str] = None
    attachment_name: Optional[str] = None
    attachment_type: Optional[str] = None
    attachment_size: Optional[int] = None
    attachment_sha256: Optional[str] = None
    timestamp: datetime
    is_ai: bool = False


class SendMessageRequest(BaseModel):
    content: str
    attachment_url: Optional[str] = None
    attachment_name: Optional[str] = None
    attachment_type: Optional[str] = None
    attachment_size: Optional[int] = None
    attachment_sha256: Optional[str] = None


class AttachmentUploadResponse(BaseModel):
    attachment_url: str
    attachment_name: str
    attachment_type: str
    attachment_size: int
    attachment_sha256: str
    threat_level: str
    risk_score: int


@router.get("/messages", response_model=List[ChatMessageResponse])
async def get_public_messages(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Returns public chat feed messages persisted in the database.
    Founder blue verified badge is displayed distinctly across messages.
    """
    stmt = (
        select(ChatMessage, User)
        .join(User, ChatMessage.sender_id == User.id)
        .where(ChatMessage.recipient_id.is_(None))
        .order_by(ChatMessage.created_at.asc())
        .limit(100)
    )
    result = await db.execute(stmt)
    rows = result.all()

    if not rows:
        # Seed initial greeting messages in database
        stmt_founder = select(User).where(User.is_founder == True)
        founder = (await db.execute(stmt_founder)).scalar_one_or_none()
        founder_id = founder.id if founder else current_user.id

        welcome_msg = ChatMessage(
            sender_id=founder_id,
            recipient_id=None,
            content="Welcome to ZeroChat AI. Your privacy and security are our highest priorities.",
            is_ai=False
        )
        db.add(welcome_msg)
        await db.commit()

        # Re-fetch
        result = await db.execute(stmt)
        rows = result.all()

    messages: List[ChatMessageResponse] = []
    for msg, user in rows:
        messages.append(
            ChatMessageResponse(
                id=msg.id,
                sender_id=user.id,
                sender_username=user.username,
                sender_email=user.email,
                recipient_id=msg.recipient_id,
                is_founder=user.is_founder,
                is_verified_badge=user.is_verified_badge,
                content=msg.content,
                attachment_url=msg.attachment_url,
                attachment_name=msg.attachment_name,
                attachment_type=msg.attachment_type,
                attachment_size=msg.attachment_size,
                attachment_sha256=msg.attachment_sha256,
                timestamp=msg.created_at,
                is_ai=msg.is_ai
            )
        )
    return messages


@router.post("/messages", response_model=ChatMessageResponse)
async def post_public_message(
    payload: SendMessageRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Posts a persistent message to the public channel.
    """
    if not payload.content.strip() and not payload.attachment_url:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Message content or attachment is required."
        )

    msg = ChatMessage(
        sender_id=current_user.id,
        recipient_id=None,
        content=payload.content.strip(),
        attachment_url=payload.attachment_url,
        attachment_name=payload.attachment_name,
        attachment_type=payload.attachment_type,
        attachment_size=payload.attachment_size,
        attachment_sha256=payload.attachment_sha256,
        is_ai=False
    )
    db.add(msg)
    await db.commit()
    await db.refresh(msg)

    return ChatMessageResponse(
        id=msg.id,
        sender_id=current_user.id,
        sender_username=current_user.username,
        sender_email=current_user.email,
        recipient_id=None,
        is_founder=current_user.is_founder,
        is_verified_badge=current_user.is_verified_badge,
        content=msg.content,
        attachment_url=msg.attachment_url,
        attachment_name=msg.attachment_name,
        attachment_type=msg.attachment_type,
        attachment_size=msg.attachment_size,
        attachment_sha256=msg.attachment_sha256,
        timestamp=msg.created_at,
        is_ai=False
    )


@router.get("/direct/{target_user_id}", response_model=List[ChatMessageResponse])
async def get_direct_messages(
    target_user_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Retrieves direct 1-to-1 message history between the authenticated user and target_user.
    Validates block status between participants.
    """
    target = await db.get(User, target_user_id)
    if not target:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Target user not found."
        )

    # Check if either user blocked the other
    block_stmt = select(UserBlock).where(
        or_(
            (UserBlock.blocker_id == target_user_id) & (UserBlock.blocked_id == current_user.id),
            (UserBlock.blocker_id == current_user.id) & (UserBlock.blocked_id == target_user_id)
        )
    )
    block_record = (await db.execute(block_stmt)).scalar_one_or_none()
    if block_record:
        if block_record.blocker_id == target_user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You cannot interact with this user because they have blocked you."
            )
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"You have blocked @{target.username}. Unblock them to view messages."
            )

    stmt = (
        select(ChatMessage, User)
        .join(User, ChatMessage.sender_id == User.id)
        .where(
            or_(
                (ChatMessage.sender_id == current_user.id) & (ChatMessage.recipient_id == target_user_id),
                (ChatMessage.sender_id == target_user_id) & (ChatMessage.recipient_id == current_user.id)
            )
        )
        .order_by(ChatMessage.created_at.asc())
        .limit(200)
    )
    result = await db.execute(stmt)
    rows = result.all()

    messages: List[ChatMessageResponse] = []
    for msg, user in rows:
        messages.append(
            ChatMessageResponse(
                id=msg.id,
                sender_id=user.id,
                sender_username=user.username,
                sender_email=user.email,
                recipient_id=msg.recipient_id,
                is_founder=user.is_founder,
                is_verified_badge=user.is_verified_badge,
                content=msg.content,
                attachment_url=msg.attachment_url,
                attachment_name=msg.attachment_name,
                attachment_type=msg.attachment_type,
                attachment_size=msg.attachment_size,
                attachment_sha256=msg.attachment_sha256,
                timestamp=msg.created_at,
                is_ai=msg.is_ai
            )
        )
    return messages


@router.post("/direct/{target_user_id}", response_model=ChatMessageResponse)
async def send_direct_message(
    target_user_id: int,
    payload: SendMessageRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Sends a direct 1-to-1 message to another user.
    Enforces block checks and persists message in the database.
    """
    if target_user_id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot send direct messages to yourself."
        )

    target = await db.get(User, target_user_id)
    if not target:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Target user not found."
        )

    # Check block list
    block_stmt = select(UserBlock).where(
        or_(
            (UserBlock.blocker_id == target_user_id) & (UserBlock.blocked_id == current_user.id),
            (UserBlock.blocker_id == current_user.id) & (UserBlock.blocked_id == target_user_id)
        )
    )
    block_record = (await db.execute(block_stmt)).scalar_one_or_none()
    if block_record:
        if block_record.blocker_id == target_user_id:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You cannot message this user because they have blocked you."
            )
        else:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"You have blocked @{target.username}. Unblock them to send messages."
            )

    if not payload.content.strip() and not payload.attachment_url:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Message content or attachment is required."
        )

    msg = ChatMessage(
        sender_id=current_user.id,
        recipient_id=target_user_id,
        content=payload.content.strip(),
        attachment_url=payload.attachment_url,
        attachment_name=payload.attachment_name,
        attachment_type=payload.attachment_type,
        attachment_size=payload.attachment_size,
        attachment_sha256=payload.attachment_sha256,
        is_ai=False
    )
    db.add(msg)
    await db.commit()
    await db.refresh(msg)

    return ChatMessageResponse(
        id=msg.id,
        sender_id=current_user.id,
        sender_username=current_user.username,
        sender_email=current_user.email,
        recipient_id=target_user_id,
        is_founder=current_user.is_founder,
        is_verified_badge=current_user.is_verified_badge,
        content=msg.content,
        attachment_url=msg.attachment_url,
        attachment_name=msg.attachment_name,
        attachment_type=msg.attachment_type,
        attachment_size=msg.attachment_size,
        attachment_sha256=msg.attachment_sha256,
        timestamp=msg.created_at,
        is_ai=False
    )


@router.post("/upload", response_model=AttachmentUploadResponse)
async def upload_attachment(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user)
):
    """
    Accepts file attachment, executes offline static inspection,
    persists safely in uploads, and returns file metadata and security score.
    """
    filename = file.filename or f"upload_{int(time.time())}.bin"
    safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", filename)
    unique_filename = f"{int(time.time())}_{safe_name}"

    try:
        content = await file.read()
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Failed to read file: {str(e)}"
        )

    if len(content) > FileSecurityAnalyzer.MAX_SAFE_FILE_SIZE:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"File exceeds maximum size limit of {FileSecurityAnalyzer.MAX_SAFE_FILE_SIZE // (1024*1024)}MB."
        )

    # Perform static offline inspection
    inspection = FileSecurityAnalyzer.inspect(safe_name, content)

    # Save to disk
    file_path = os.path.join(UPLOAD_DIR, unique_filename)
    async with aiofiles.open(file_path, "wb") as f:
        await f.write(content)

    return AttachmentUploadResponse(
        attachment_url=f"/uploads/{unique_filename}",
        attachment_name=filename,
        attachment_type=file.content_type or "application/octet-stream",
        attachment_size=len(content),
        attachment_sha256=inspection.get("sha256", ""),
        threat_level=inspection.get("threat_level", "SAFE"),
        risk_score=inspection.get("risk_score", 0)
    )


@router.get("/users/search", response_model=List[UserResponse])
async def search_users(
    q: str = Query(..., min_length=1),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Searches users for group chats or direct messages.
    Displays blue verified badge and founder status in search results.
    """
    pattern = f"%{q.strip().lower()}%"
    stmt = (
        select(User)
        .where(
            (func.lower(User.username).like(pattern)) |
            (func.lower(User.email).like(pattern))
        )
        .limit(20)
    )
    res = await db.execute(stmt)
    users = res.scalars().all()
    return [UserResponse.model_validate(u) for u in users]
