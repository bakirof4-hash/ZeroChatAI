import io
import pytest
from app.core.security import create_access_token
from app.models.user import User, UserRole


@pytest.mark.asyncio
async def test_public_chat_feed(client, founder_user):
    token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers = {"Authorization": f"Bearer {token}"}

    # Fetch initial feed (seeds welcome message)
    feed_res = await client.get("/api/v1/chat/messages", headers=headers)
    assert feed_res.status_code == 200
    messages = feed_res.json()
    assert len(messages) >= 1
    assert messages[0]["is_founder"] is True

    # Post public message
    post_res = await client.post(
        "/api/v1/chat/messages",
        json={"content": "Founder announcement: ZeroChat AI 2.0 is live."},
        headers=headers
    )
    assert post_res.status_code == 200
    msg = post_res.json()
    assert msg["content"] == "Founder announcement: ZeroChat AI 2.0 is live."
    assert msg["is_founder"] is True
    assert msg["is_verified_badge"] is True


@pytest.mark.asyncio
async def test_direct_messaging_and_blocking(client, db_session, founder_user):
    user = User(
        email="charlie@test.com",
        username="charlie_dev",
        hashed_password="hash",
        role=UserRole.USER.value,
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(user)
    await db_session.commit()
    await db_session.refresh(user)

    founder_token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    founder_headers = {"Authorization": f"Bearer {founder_token}"}

    charlie_token = create_access_token({"sub": str(user.id), "email": user.email, "role": user.role})
    charlie_headers = {"Authorization": f"Bearer {charlie_token}"}

    # Founder sends direct message to Charlie
    send_res = await client.post(
        f"/api/v1/chat/direct/{user.id}",
        json={"content": "Hello Charlie, welcome to ZeroChat private channel."},
        headers=founder_headers
    )
    assert send_res.status_code == 200
    assert send_res.json()["recipient_id"] == user.id

    # Charlie reads direct messages
    history_res = await client.get(f"/api/v1/chat/direct/{founder_user.id}", headers=charlie_headers)
    assert history_res.status_code == 200
    history = history_res.json()
    assert len(history) == 1
    assert history[0]["content"] == "Hello Charlie, welcome to ZeroChat private channel."
    assert history[0]["is_founder"] is True

    # Charlie replies
    reply_res = await client.post(
        f"/api/v1/chat/direct/{founder_user.id}",
        json={"content": "Thank you Founder! Thrilled to be here."},
        headers=charlie_headers
    )
    assert reply_res.status_code == 200

    # Charlie blocks Founder
    await client.post(f"/api/v1/users/{founder_user.id}/block", headers=charlie_headers)

    # Founder tries to send another message -> Rejected 403
    blocked_send = await client.post(
        f"/api/v1/chat/direct/{user.id}",
        json={"content": "Are you there?"},
        headers=founder_headers
    )
    assert blocked_send.status_code == 403


@pytest.mark.asyncio
async def test_attachment_upload_and_scan(client, founder_user):
    token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers = {"Authorization": f"Bearer {token}"}

    # Upload a safe text file
    file_content = b"Sample safe log content for ZeroChat attachment testing."
    files = {"file": ("test_log.txt", io.BytesIO(file_content), "text/plain")}

    upload_res = await client.post("/api/v1/chat/upload", files=files, headers=headers)
    assert upload_res.status_code == 200
    data = upload_res.json()
    assert data["attachment_name"] == "test_log.txt"
    assert data["threat_level"] in ["CLEAN", "SAFE"]
    assert data["risk_score"] == 0
    assert data["attachment_url"].startswith("/uploads/")
