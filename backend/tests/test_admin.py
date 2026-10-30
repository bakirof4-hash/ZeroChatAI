import pytest
from app.core.security import create_access_token, hash_password
from app.models.user import User


@pytest.mark.asyncio
async def test_admin_rbac_rejection_for_ordinary_users(client, db_session):
    """
    Ordinary users attempting to access admin endpoints are rejected with 403 Forbidden.
    Enforces authorization on the backend, not just in UI!
    """
    # Create normal user
    normal_user = User(
        email="normal@zerochat.ai",
        username="NormalUser",
        hashed_password=hash_password("NormalPass123!"),
        role="USER",
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(normal_user)
    await db_session.commit()
    await db_session.refresh(normal_user)

    normal_token = create_access_token({"sub": str(normal_user.id), "email": normal_user.email, "role": "USER"})
    headers = {"Authorization": f"Bearer {normal_token}"}

    # Attempt to access admin dashboard stats -> 403 Forbidden
    res_stats = await client.get("/api/v1/admin/stats", headers=headers)
    assert res_stats.status_code == 403
    assert "Administrator privileges required" in res_stats.json()["detail"]

    # Attempt to list users -> 403 Forbidden
    res_users = await client.get("/api/v1/admin/users", headers=headers)
    assert res_users.status_code == 403

    # Attempt to assign badge to self -> 403 Forbidden
    res_badge = await client.post(
        f"/api/v1/admin/users/{normal_user.id}/badge",
        json={"is_verified_badge": True, "reason": "Self grant attempt"},
        headers=headers
    )
    assert res_badge.status_code == 403


@pytest.mark.asyncio
async def test_founder_super_admin_access_and_badge_management(client, db_session, founder_user):
    """
    Founder has Super Admin role, blue verified badge, and can manage users and badges.
    """
    # Create a regular target user
    target_user = User(
        email="target@zerochat.ai",
        username="TargetUser",
        hashed_password=hash_password("TargetPass123!"),
        role="USER",
        is_founder=False,
        is_verified_badge=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(target_user)
    await db_session.commit()
    await db_session.refresh(target_user)

    founder_token = create_access_token({
        "sub": str(founder_user.id),
        "email": founder_user.email,
        "role": founder_user.role,
        "is_founder": True
    })
    founder_headers = {"Authorization": f"Bearer {founder_token}"}

    # 1. Founder accesses admin stats
    res_stats = await client.get("/api/v1/admin/stats", headers=founder_headers)
    assert res_stats.status_code == 200
    stats = res_stats.json()
    assert stats["total_users"] >= 2

    # 2. Founder assigns blue verified badge to target user
    res_badge = await client.post(
        f"/api/v1/admin/users/{target_user.id}/badge",
        json={"is_verified_badge": True, "reason": "Verified community contributor"},
        headers=founder_headers
    )
    assert res_badge.status_code == 200
    assert res_badge.json()["is_verified_badge"] is True

    # 3. Check audit logs to verify server-side record
    res_logs = await client.get("/api/v1/admin/audit-logs", headers=founder_headers)
    assert res_logs.status_code == 200
    logs = res_logs.json()
    assert any(log["action"] == "ASSIGN_VERIFIED_BADGE" for log in logs)

    # 4. Attempt to revoke Founder's own blue badge -> 400 Bad Request
    res_revoke_founder = await client.post(
        f"/api/v1/admin/users/{founder_user.id}/badge",
        json={"is_verified_badge": False, "reason": "Invalid attempt"},
        headers=founder_headers
    )
    assert res_revoke_founder.status_code == 400
    assert "Founder's blue verification badge cannot be revoked" in res_revoke_founder.json()["detail"]

