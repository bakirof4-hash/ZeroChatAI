import io
import pytest
from app.core.security import create_access_token, hash_password
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


@pytest.mark.asyncio
async def test_conversations_endpoint(client, db_session):
    """
    Tests the Telegram-inspired conversations list endpoint.
    Returns the pinned public community chat and direct 1-to-1 conversations.
    """
    u1 = User(
        email="alice@zerochat.ai",
        username="Alice",
        hashed_password=hash_password("Pass123!"),
        role="USER",
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    u2 = User(
        email="bob@zerochat.ai",
        username="Bob",
        hashed_password=hash_password("Pass123!"),
        role="USER",
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add_all([u1, u2])
    await db_session.commit()
    await db_session.refresh(u1)
    await db_session.refresh(u2)

    token1 = create_access_token({"sub": str(u1.id), "email": u1.email, "role": "USER"})
    headers1 = {"Authorization": f"Bearer {token1}"}

    # Post public message
    await client.post("/api/v1/chat/messages", json={"content": "Hello community!"}, headers=headers1)

    # Send direct message from Alice to Bob
    await client.post(f"/api/v1/chat/direct/{u2.id}", json={"content": "Hey Bob!"}, headers=headers1)

    # Fetch conversations for Alice
    res = await client.get("/api/v1/chat/conversations", headers=headers1)
    assert res.status_code == 200
    convs = res.json()

    # Must contain public chat and direct chat with Bob
    assert len(convs) >= 2
    public_conv = next((c for c in convs if c["conversation_id"] == "public"), None)
    assert public_conv is not None
    assert public_conv["is_group"] is True
    assert "Hello community!" in public_conv["last_message"]

    bob_conv = next((c for c in convs if c["conversation_id"] == f"direct_{u2.id}"), None)
    assert bob_conv is not None
    assert bob_conv["is_group"] is False
    assert bob_conv["title"] == "Bob"
    assert "Hey Bob!" in bob_conv["last_message"]

