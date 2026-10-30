from typing import Any, Dict, List
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from pydantic import BaseModel
from app.api.deps import get_current_user
from app.models.user import User
from app.services.file_analyzer import FileSecurityAnalyzer

router = APIRouter(prefix="/security", tags=["Offline File Security"])


class InspectionFinding(BaseModel):
    severity: str
    message: str


class FileInspectionResponse(BaseModel):
    filename: str
    file_type: str
    file_size: int
    sha256: str
    threat_level: str
    risk_score: int
    findings: List[InspectionFinding]
    disclaimer: str


@router.post("/inspect-file", response_model=FileInspectionResponse)
async def inspect_file_security(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user)
):
    """
    Offline Static Security Inspection for APK, EXE, DLL, and other files.
    - Inspects APKs without installing.
    - Inspects EXEs without executing.
    - Operates locally without external cloud AI dependencies.
    - Flags dangerous permissions, code injection imports, high-entropy packed sections.
    - Provides a clearly labeled static-analysis fallback and safety disclaimer.
    """
    filename = file.filename or "unknown_file"

    try:
        content = await file.read()
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unable to read uploaded file: {str(e)}"
        )

    if not content:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Uploaded file is empty (0 bytes)."
        )

    result = FileSecurityAnalyzer.inspect(filename, content)

    findings = [
        InspectionFinding(severity=f.get("severity", "INFO"), message=f.get("message", ""))
        for f in result.get("findings", [])
    ]

    return FileInspectionResponse(
        filename=result.get("filename", filename),
        file_type=result.get("file_type", "Unknown"),
        file_size=result.get("file_size", len(content)),
        sha256=result.get("sha256", ""),
        threat_level=result.get("threat_level", "SAFE"),
        risk_score=result.get("risk_score", 0),
        findings=findings,
        disclaimer=result.get(
            "disclaimer",
            "Heuristic static analysis inspects structural headers, sections, and symbols without executing code. It cannot guarantee detection of all malicious files."
        )
    )
