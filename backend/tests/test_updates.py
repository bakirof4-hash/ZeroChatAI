import hashlib
import io
import pytest
from app.core.security import create_access_token
from app.models.user import User, UserRole


@pytest.mark.asyncio
async def test_get_latest_version_baseline(client):
    res = await client.get("/api/v1/app/version/latest")
    assert res.status_code == 200
    data = res.json()
    assert data["version_code"] >= 1
    assert data["version_name"] != ""
    assert "apk_sha256" in data
    assert "release_notes" in data


@pytest.mark.asyncio
async def test_admin_publish_version_rbac(client, db_session, founder_user):
    # Regular user
    regular = User(
        email="regular@test.com",
        username="reg_user",
        hashed_password="hash",
        role=UserRole.USER.value,
        is_founder=False,
        is_active=True,
        email_verified=True
    )
    db_session.add(regular)
    await db_session.commit()
    await db_session.refresh(regular)

    reg_token = create_access_token({"sub": str(regular.id), "email": regular.email, "role": regular.role})
    headers_reg = {"Authorization": f"Bearer {reg_token}"}

    founder_token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers_founder = {"Authorization": f"Bearer {founder_token}"}

    payload = {
        "version_code": 2,
        "version_name": "1.1.0",
        "release_notes": "Added real-time messaging and in-app update checks.",
        "apk_url": "/api/v1/app/download/zerochat_v2.apk",
        "apk_sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "apk_size": 19000000,
        "is_mandatory": False,
        "min_supported_version_code": 1
    }

    # 1. Regular user rejected (403 Forbidden)
    res_reg = await client.post("/api/v1/app/version", json=payload, headers=headers_reg)
    assert res_reg.status_code == 403

    # 2. Super Admin / Founder allowed
    res_admin = await client.post("/api/v1/app/version", json=payload, headers=headers_founder)
    assert res_admin.status_code == 200
    data = res_admin.json()
    assert data["version_code"] == 2
    assert data["version_name"] == "1.1.0"

    # 3. Public latest version now reports version 2
    res_latest = await client.get("/api/v1/app/version/latest")
    assert res_latest.status_code == 200
    assert res_latest.json()["version_code"] == 2
    assert res_latest.json()["version_name"] == "1.1.0"


@pytest.mark.asyncio
async def test_admin_upload_apk(client, founder_user):
    founder_token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers_founder = {"Authorization": f"Bearer {founder_token}"}

    apk_dummy_bytes = b"PK\x03\x04" + b"DummyAPKDataForTestingSHA256ChecksumCalculation" * 20
    expected_sha256 = hashlib.sha256(apk_dummy_bytes).hexdigest().lower()

    files = {"file": ("zerochat-release.apk", apk_dummy_bytes, "application/vnd.android.package-archive")}
    data = {
        "version_code": 3,
        "version_name": "1.2.0",
        "release_notes": "Critical security updates and encrypted local cache.",
        "is_mandatory": "true",
        "min_supported_version_code": "2"
    }

    res = await client.post("/api/v1/app/upload-apk", files=files, data=data, headers=headers_founder)
    assert res.status_code == 200
    resp_data = res.json()
    assert resp_data["version_code"] == 3
    assert resp_data["version_name"] == "1.2.0"
    assert resp_data["apk_sha256"] == expected_sha256
    assert resp_data["apk_size"] == len(apk_dummy_bytes)
    assert resp_data["is_mandatory"] is True

    # Test download of the uploaded package
    download_url = resp_data["apk_url"]
    dl_res = await client.get(download_url)
    assert dl_res.status_code == 200
    assert dl_res.content == apk_dummy_bytes
    assert dl_res.headers["content-type"] == "application/vnd.android.package-archive"

