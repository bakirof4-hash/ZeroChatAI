import hashlib
import io
import math
import struct
import zipfile
from typing import Any, Dict, List, Optional


def calculate_entropy(data: bytes) -> float:
    """Calculates Shannon entropy of a byte sequence (0.0 to 8.0)."""
    if not data:
        return 0.0
    entropy = 0.0
    length = len(data)
    counts = [0] * 256
    for b in data:
        counts[b] += 1
    for count in counts:
        if count == 0:
            continue
        p = count / length
        entropy -= p * math.log2(p)
    return entropy


class FileSecurityAnalyzer:
    """
    Offline Static & Heuristic Security Analyzer.
    
    Inspects binaries (APK, EXE) and documents WITHOUT executing or installing them.
    Operates completely locally with rule-based structural parsing.
    
    DISCLAIMER:
    Heuristic static analysis inspects file headers, sections, permissions,
    entropy, and imported symbols. It does not execute the binary. While highly
    effective at flagging suspicious structural anomalies and high-risk indicators,
    heuristic analysis cannot guarantee the detection of all novel or heavily obfuscated malware.
    """

    MAX_SAFE_FILE_SIZE = 50 * 1024 * 1024  # 50 MB

    DANGEROUS_ANDROID_PERMISSIONS = {
        "android.permission.RECEIVE_BOOT_COMPLETED": (15, "Executes automatically on device boot"),
        "android.permission.SYSTEM_ALERT_WINDOW": (20, "Can draw overlays over other applications (screen hijacking risk)"),
        "android.permission.REQUEST_INSTALL_PACKAGES": (25, "Can silently prompt to install secondary APKs"),
        "android.permission.BIND_DEVICE_ADMIN": (30, "Requests Device Administration privileges"),
        "android.permission.READ_SMS": (15, "Can read incoming SMS messages (OTP interception risk)"),
        "android.permission.SEND_SMS": (20, "Can send premium SMS messages"),
        "android.permission.RECORD_AUDIO": (10, "Accesses device microphone"),
        "android.permission.CAMERA": (10, "Accesses device camera"),
        "android.permission.READ_CALL_LOG": (15, "Accesses phone call logs"),
        "android.permission.READ_CONTACTS": (10, "Accesses address book contacts"),
    }

    SUSPICIOUS_WIN32_IMPORTS = {
        b"VirtualAllocEx": (20, "Allocates memory in a remote process (injection indicator)"),
        b"WriteProcessMemory": (25, "Writes into remote process memory (code injection indicator)"),
        b"CreateRemoteThread": (25, "Executes code inside a remote process thread"),
        b"SetWindowsHookEx": (20, "Installs system-wide hook (keylogger indicator)"),
        b"GetAsyncKeyState": (15, "Monitors keystrokes asynchronously"),
        b"URLDownloadToFile": (15, "Downloads and writes files from remote web servers"),
        b"InternetOpen": (10, "Initiates WinINet network connection"),
        b"HttpSendRequest": (10, "Transmits HTTP payloads"),
        b"RegSetValueEx": (10, "Modifies Windows Registry (persistence indicator)"),
        b"WinExec": (15, "Launches shell commands directly"),
    }

    @classmethod
    def inspect(cls, filename: str, data: bytes) -> Dict[str, Any]:
        """Runs offline static inspection on file content."""
        file_size = len(data)
        sha256 = hashlib.sha256(data).hexdigest()
        ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""

        findings: List[Dict[str, str]] = []
        risk_score = 0
        file_type = "Generic File"

        # Check file size limit
        if file_size > cls.MAX_SAFE_FILE_SIZE:
            return {
                "filename": filename,
                "file_type": "Oversized File",
                "file_size": file_size,
                "sha256": sha256,
                "threat_level": "SUSPICIOUS",
                "risk_score": 60,
                "findings": [{"severity": "HIGH", "message": f"File size exceeds maximum safe scan threshold ({file_size} bytes)"}],
                "disclaimer": "Analysis truncated due to excessive file size."
            }

        if ext == "apk" or data[:4] == b"PK\x03\x04":
            apk_result = cls._inspect_apk(data)
            findings.extend(apk_result["findings"])
            risk_score += apk_result["risk_score"]
            file_type = "Android Application Package (APK)"

        elif ext in ["exe", "dll"] or data[:2] == b"MZ":
            exe_result = cls._inspect_exe(data)
            findings.extend(exe_result["findings"])
            risk_score += exe_result["risk_score"]
            file_type = "Windows Portable Executable (PE)"

        else:
            # General file entropy check
            entropy = calculate_entropy(data)
            if entropy > 7.85:
                findings.append({"severity": "LOW", "message": f"High entropy ({entropy:.2f}/8.0). File content is compressed or encrypted."})
                risk_score += 15

        # Normalize risk score 0 - 100
        risk_score = min(100, risk_score)

        if risk_score >= 70:
            threat_level = "CRITICAL"
        elif risk_score >= 45:
            threat_level = "HIGH"
        elif risk_score >= 20:
            threat_level = "MEDIUM"
        elif risk_score > 0:
            threat_level = "LOW"
        else:
            threat_level = "CLEAN"

        return {
            "filename": filename,
            "file_type": file_type,
            "file_size": file_size,
            "sha256": sha256,
            "threat_level": threat_level,
            "risk_score": risk_score,
            "findings": findings,
            "engine": "ZeroChat Offline Heuristic Static Engine v1.0",
            "disclaimer": (
                "Static analysis examines file structures, headers, entropy, permissions, and imported symbols "
                "without execution. It provides robust preliminary screening, but cannot guarantee detection of all 0-day threats."
            )
        }

    @classmethod
    def _inspect_apk(cls, data: bytes) -> Dict[str, Any]:
        """Inspects an Android APK without installing it."""
        findings = []
        risk_score = 0

        try:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                namelist = zf.namelist()

                # 1. Structural check
                has_manifest = "AndroidManifest.xml" in namelist
                has_dex = any(name.startswith("classes") and name.endswith(".dex") for name in namelist)
                has_signature = any(name.startswith("META-INF/") for name in namelist)

                if not has_manifest:
                    findings.append({"severity": "CRITICAL", "message": "Malformed APK: Missing AndroidManifest.xml"})
                    risk_score += 40
                if not has_dex:
                    findings.append({"severity": "CRITICAL", "message": "Suspicious APK: No DEX bytecode found"})
                    risk_score += 35
                if not has_signature:
                    findings.append({"severity": "HIGH", "message": "Unsigned APK: Missing cryptographic META-INF signature"})
                    risk_score += 30

                # 2. Inspect classes.dex header if present
                for name in namelist:
                    if name.startswith("classes") and name.endswith(".dex"):
                        dex_bytes = zf.read(name)
                        if len(dex_bytes) > 8:
                            dex_magic = dex_bytes[:8]
                            if not (dex_magic.startswith(b"dex\n") and dex_magic.endswith(b"\0")):
                                findings.append({"severity": "HIGH", "message": f"Corrupted or spoofed DEX header in {name}"})
                                risk_score += 25

                        # Scan for suspicious native loaders in bytecode strings
                        if b"Runtime;->exec" in dex_bytes or b"ProcessBuilder" in dex_bytes:
                            findings.append({"severity": "MEDIUM", "message": f"Contains shell execution calls (Runtime.exec) in {name}"})
                            risk_score += 15

                # 3. Inspect AndroidManifest for sensitive permissions
                if has_manifest:
                    manifest_raw = zf.read("AndroidManifest.xml")
                    for perm, (weight, desc) in cls.DANGEROUS_ANDROID_PERMISSIONS.items():
                        perm_bytes = perm.encode("utf-8")
                        if perm_bytes in manifest_raw:
                            findings.append({"severity": "MEDIUM", "message": f"High-risk permission: {perm} ({desc})"})
                            risk_score += weight

                if not findings:
                    findings.append({"severity": "INFO", "message": "APK structure valid. Standard DEX headers and valid manifest detected."})

        except zipfile.BadZipFile:
            findings.append({"severity": "CRITICAL", "message": "Corrupted or obfuscated ZIP archive structure"})
            risk_score += 60
        except Exception as e:
            findings.append({"severity": "HIGH", "message": f"Scanner error during APK parsing: {str(e)}"})
            risk_score += 20

        return {"findings": findings, "risk_score": risk_score}

    @classmethod
    def _inspect_exe(cls, data: bytes) -> Dict[str, Any]:
        """Inspects a Windows Portable Executable (EXE/DLL) without executing it."""
        findings = []
        risk_score = 0

        try:
            if len(data) < 64:
                return {"findings": [{"severity": "CRITICAL", "message": "File too small to be a valid PE binary"}], "risk_score": 80}

            # 1. DOS Header check: MZ (0x5A4D)
            if data[:2] != b"MZ":
                findings.append({"severity": "CRITICAL", "message": "Invalid DOS Header magic (expected 'MZ')"})
                risk_score += 50
                return {"findings": findings, "risk_score": risk_score}

            # 2. Offset to PE Header at 0x3C
            pe_offset = struct.unpack("<I", data[0x3C:0x40])[0]
            if pe_offset + 4 > len(data):
                findings.append({"severity": "CRITICAL", "message": "PE header pointer points outside binary bounds"})
                risk_score += 60
                return {"findings": findings, "risk_score": risk_score}

            # 3. PE Signature check: PE\0\0 (0x50, 0x45, 0x00, 0x00)
            pe_sig = data[pe_offset:pe_offset + 4]
            if pe_sig != b"PE\x00\x00":
                findings.append({"severity": "CRITICAL", "message": "Invalid PE Signature (expected 'PE\\0\\0')"})
                risk_score += 50
                return {"findings": findings, "risk_score": risk_score}

            # 4. COFF Header
            coff_header = data[pe_offset + 4:pe_offset + 24]
            machine, num_sections, timedate, sym_ptr, num_syms, opt_hdr_size, characteristics = struct.unpack(
                "<HHIIIHH", coff_header
            )

            # Check section count
            if num_sections == 0 or num_sections > 40:
                findings.append({"severity": "HIGH", "message": f"Abnormal section count: {num_sections}"})
                risk_score += 20

            # 5. Parse Section Headers
            section_table_offset = pe_offset + 24 + opt_hdr_size
            for i in range(min(num_sections, 20)):
                sec_off = section_table_offset + (i * 40)
                if sec_off + 40 > len(data):
                    break
                sec_name_raw = data[sec_off:sec_off + 8].split(b"\x00")[0]
                sec_name = sec_name_raw.decode("ascii", errors="replace")
                virt_size, virt_addr, raw_size, raw_ptr, _, _, _, _, sec_flags = struct.unpack(
                    "<IIIIIIHHI", data[sec_off + 8:sec_off + 40]
                )

                # Check for Writable AND Executable sections (classic injection / unpacker shellcode)
                IMAGE_SCN_MEM_EXECUTE = 0x20000000
                IMAGE_SCN_MEM_WRITE = 0x80000000
                if (sec_flags & IMAGE_SCN_MEM_EXECUTE) and (sec_flags & IMAGE_SCN_MEM_WRITE):
                    findings.append({
                        "severity": "CRITICAL",
                        "message": f"Section '{sec_name}' is both WRITABLE and EXECUTABLE (Self-modifying / Code Injection risk)"
                    })
                    risk_score += 35

                # Section entropy check
                if raw_ptr + raw_size <= len(data) and raw_size > 512:
                    sec_bytes = data[raw_ptr:raw_ptr + raw_size]
                    sec_entropy = calculate_entropy(sec_bytes)
                    if sec_entropy > 7.2:
                        findings.append({
                            "severity": "HIGH",
                            "message": f"Section '{sec_name}' has high entropy ({sec_entropy:.2f}/8.0), indicating packing or encryption (e.g. UPX packer)"
                        })
                        risk_score += 25

            # 6. Check for suspicious Win32 API symbol imports
            for symbol, (weight, desc) in cls.SUSPICIOUS_WIN32_IMPORTS.items():
                if symbol in data:
                    findings.append({
                        "severity": "MEDIUM",
                        "message": f"Imported API '{symbol.decode()}': {desc}"
                    })
                    risk_score += weight

            if not findings:
                findings.append({"severity": "INFO", "message": "Valid PE binary headers with normal section entropy and no injection markers."})

        except Exception as e:
            findings.append({"severity": "HIGH", "message": f"Error during PE parsing: {str(e)}"})
            risk_score += 20

        return {"findings": findings, "risk_score": risk_score}
