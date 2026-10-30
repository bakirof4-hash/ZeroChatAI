import pytest
from app.core.security import create_access_token
from app.models.user import User, UserRole


@pytest.mark.asyncio
async def test_username_check(client, founder_user):
    # Check already taken username
    res_taken = await client.get("/api/v1/auth/check-username?username=FounderAdmin")
    assert res_taken.status_code == 200
    data_taken = res_taken.json()
    assert data_taken["available"] is False

    # Check available username
    res_avail = await client.get("/api/v1/auth/check-username?username=cyber_agent")
    assert res_avail.status_code == 200
    data_avail = res_avail.json()
    assert data_avail["available"] is True

    # Check invalid format (contains invalid symbols)
    res_invalid = await client.get("/api/v1/auth/check-username?username=bad!user")
    assert res_invalid.status_code == 200
    data_inv = res_invalid.json()
    assert data_inv["available"] is False

    # Check too short (< 3 chars) -> 422
    res_short = await client.get("/api/v1/auth/check-username?username=ab")
    assert res_short.status_code == 422


@pytest.mark.asyncio
async def test_qr_code_generation_and_scan(client, db_session, founder_user):
    # Create regular user
    user2 = User(
        email="alice@test.com",
        username="alice_zero",
        hashed_password="hash",
        role=UserRole.USER.value,
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(user2)
    await db_session.commit()
    await db_session.refresh(user2)

    token = create_access_token({"sub": str(user2.id), "email": user2.email, "role": user2.role})
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Get my QR code info
    qr_res = await client.get("/api/v1/users/me/qr", headers=headers)
    assert qr_res.status_code == 200
    qr_data = qr_res.json()
    assert qr_data["username"] == "alice_zero"
    assert "zerochat://user?u=alice_zero" in qr_data["qr_payload"]

    # 2. Get QR PNG image
    img_res = await client.get("/api/v1/users/alice_zero/qr.png")
    assert img_res.status_code == 200
    assert img_res.headers["content-type"] == "image/png"
    assert len(img_res.content) > 100

    # 3. Lookup user by username
    lookup_res = await client.get("/api/v1/users/lookup?username=FounderAdmin", headers=headers)
    assert lookup_res.status_code == 200
    assert lookup_res.json()["is_founder"] is True
    assert lookup_res.json()["is_verified_badge"] is True

    # 4. Scan valid QR code
    scan_res = await client.post(
        "/api/v1/users/qr-scan",
        json={"qr_content": "zerochat://user?u=FounderAdmin&uid=1"},
        headers=headers
    )
    assert scan_res.status_code == 200
    assert scan_res.json()["user"]["username"] == "FounderAdmin"
    assert scan_res.json()["user"]["is_founder"] is True

    # 5. Scan invalid QR code
    scan_err = await client.post(
        "/api/v1/users/qr-scan",
        json={"qr_content": "zerochat://user?u=nonexistent_user"},
        headers=headers
    )
    assert scan_err.status_code == 404


@pytest.mark.asyncio
async def test_user_blocking(client, db_session, founder_user):
    # Create target user
    bob = User(
        email="bob@test.com",
        username="bob_user",
        hashed_password="hash",
        role=UserRole.USER.value,
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(bob)
    await db_session.commit()
    await db_session.refresh(bob)

    founder_token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers_founder = {"Authorization": f"Bearer {founder_token}"}

    bob_token = create_access_token({"sub": str(bob.id), "email": bob.email, "role": bob.role})
    headers_bob = {"Authorization": f"Bearer {bob_token}"}

    # Founder blocks Bob
    block_res = await client.post(f"/api/v1/users/{bob.id}/block", headers=headers_founder)
    assert block_res.status_code == 200

    # Check blocked list
    blocked_list = await client.get("/api/v1/users/blocked", headers=headers_founder)
    assert blocked_list.status_code == 200
    assert len(blocked_list.json()) == 1
    assert blocked_list.json()[0]["id"] == bob.id

    # Bob tries to scan Founder's QR -> Rejected 403 Forbidden
    bob_scan = await client.post(
        "/api/v1/users/qr-scan",
        json={"qr_content": f"zerochat://user?u={founder_user.username}"},
        headers=headers_bob
    )
    assert bob_scan.status_code == 403

    # Founder unblocks Bob
    unblock_res = await client.post(f"/api/v1/users/{bob.id}/unblock", headers=headers_founder)
    assert unblock_res.status_code == 200

    # Bob can scan again
    bob_scan_ok = await client.post(
        "/api/v1/users/qr-scan",
        json={"qr_content": f"zerochat://user?u={founder_user.username}"},
        headers=headers_bob
    )
    assert bob_scan_ok.status_code == 200


@pytest.mark.asyncio
async def test_qr_contact_saving_and_deletion(client, db_session, founder_user):
    # Create contact target user
    carol = User(
        email="carol@test.com",
        username="carol_user",
        hashed_password="hash",
        role=UserRole.USER.value,
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(carol)
    await db_session.commit()
    await db_session.refresh(carol)

    founder_token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers_founder = {"Authorization": f"Bearer {founder_token}"}

    # 1. Save contact
    save_res = await client.post(
        "/api/v1/users/contacts",
        json={
            "contact_user_id": carol.id,
            "qr_payload": f"zerochat://user?u={carol.username}&uid={carol.id}",
            "nickname": "Carol Colleague"
        },
        headers=headers_founder
    )
    assert save_res.status_code == 200
    contact_data = save_res.json()
    assert contact_data["contact_user"]["username"] == "carol_user"
    assert contact_data["nickname"] == "Carol Colleague"

    # 2. Cannot add self
    self_res = await client.post(
        "/api/v1/users/contacts",
        json={"contact_user_id": founder_user.id},
        headers=headers_founder
    )
    assert self_res.status_code == 400

    # 3. List contacts
    list_res = await client.get("/api/v1/users/contacts", headers=headers_founder)
    assert list_res.status_code == 200
    assert len(list_res.json()) == 1
    assert list_res.json()[0]["contact_user"]["id"] == carol.id

    # 4. Delete contact
    del_res = await client.delete(f"/api/v1/users/contacts/{carol.id}", headers=headers_founder)
    assert del_res.status_code == 200

    # 5. Verify list is empty
    list_after = await client.get("/api/v1/users/contacts", headers=headers_founder)
    assert list_after.status_code == 200
    assert len(list_after.json()) == 0
