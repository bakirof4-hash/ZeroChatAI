import io
import struct
import zipfile
import pytest
from app.core.security import create_access_token
from app.services.file_analyzer import FileSecurityAnalyzer, calculate_entropy


def test_entropy_calculation():
    # Empty bytes has 0 entropy
    assert calculate_entropy(b"") == 0.0

    # Low entropy (all identical bytes)
    assert calculate_entropy(b"AAAAAAA") == 0.0

    # High entropy (all 256 unique bytes)
    high_entropy_data = bytes(range(256))
    assert calculate_entropy(high_entropy_data) == 8.0


def test_apk_static_inspection():
    # Construct a minimal mock APK (ZIP archive with AndroidManifest.xml and classes.dex)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        # Minimal dummy manifest containing dangerous permission strings
        manifest_data = (
            b"android.permission.RECEIVE_BOOT_COMPLETED\x00"
            b"android.permission.SYSTEM_ALERT_WINDOW\x00"
            b"android.permission.SEND_SMS\x00"
        )
        z.writestr("AndroidManifest.xml", manifest_data)
        z.writestr("classes.dex", b"dex\n035\x00" + b"\x00" * 100)

    apk_bytes = buf.getvalue()
    result = FileSecurityAnalyzer.inspect("sample_app.apk", apk_bytes)

    assert result["file_type"] == "Android Application Package (APK)"
    assert result["threat_level"] in ["SUSPICIOUS", "HIGH", "CRITICAL"]
    assert result["risk_score"] > 0
    assert any("SYSTEM_ALERT_WINDOW" in f["message"] for f in result["findings"])
    assert any("SEND_SMS" in f["message"] for f in result["findings"])
    assert "disclaimer" in result


def test_exe_static_inspection():
    # Construct minimal PE MZ header
    dos_header = bytearray(64)
    dos_header[0:2] = b"MZ"
    # e_lfanew at offset 0x3C points to PE offset 64
    struct.pack_into("<I", dos_header, 0x3C, 64)

    pe_header = bytearray()
    pe_header.extend(b"PE\x00\x00")
    # COFF Machine=0x8664 (x64), NumberOfSections=1
    pe_header.extend(struct.pack("<HHIIIHH", 0x8664, 1, 0, 0, 0, 0xF0, 0x0022))

    # Optional header
    pe_header.extend(b"\x00" * 0xF0)

    # Section header
    section = bytearray(40)
    section[0:5] = b".text"
    struct.pack_into("<IIIIIIHHI", section, 8, 512, 0x1000, 512, len(dos_header) + len(pe_header) + 40, 0, 0, 0, 0, 0x60000020)
    pe_header.extend(section)

    # Section data containing suspicious imports
    section_data = b"SomeCode VirtualAllocEx WriteProcessMemory CreateRemoteThread End"
    full_binary = bytes(dos_header) + bytes(pe_header) + section_data

    result = FileSecurityAnalyzer.inspect("payload.exe", full_binary)
    assert "Portable Executable" in result["file_type"]
    assert result["risk_score"] > 0
    assert any("WriteProcessMemory" in f["message"] for f in result["findings"])


@pytest.mark.asyncio
async def test_security_inspect_endpoint(client, founder_user):
    token = create_access_token({"sub": str(founder_user.id), "email": founder_user.email, "role": founder_user.role})
    headers = {"Authorization": f"Bearer {token}"}

    # Upload mock APK
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("AndroidManifest.xml", b"android.permission.BIND_DEVICE_ADMIN")
        z.writestr("classes.dex", b"dex\n035\x00")

    files = {"file": ("malicious_test.apk", buf.getvalue(), "application/vnd.android.package-archive")}
    res = await client.post("/api/v1/security/inspect-file", files=files, headers=headers)

    assert res.status_code == 200
    data = res.json()
    assert data["filename"] == "malicious_test.apk"
    assert data["threat_level"] in ["SUSPICIOUS", "HIGH", "CRITICAL"]
    assert len(data["findings"]) > 0
    assert "disclaimer" in data
    assert data["id"] is not None

    # Verify history retrieval
    hist_res = await client.get("/api/v1/security/history", headers=headers)
    assert hist_res.status_code == 200
    history = hist_res.json()
    assert len(history) >= 1
    assert history[0]["filename"] == "malicious_test.apk"
