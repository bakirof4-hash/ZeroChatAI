import json
from typing import Any, Dict, List, Optional
from datetime import datetime
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.api.deps import get_current_user
from app.db.session import get_db
from app.models.file_inspection import FileInspection
from app.models.user import User
from app.services.file_analyzer import FileSecurityAnalyzer

router = APIRouter(prefix="/security", tags=["Offline File Security"])


class InspectionFinding(BaseModel):
    severity: str
    message: str


class FileInspectionResponse(BaseModel):
    id: Optional[int] = None
    filename: str
    file_type: str
    file_size: int
    sha256: str
    threat_level: str
    risk_score: int
    findings: List[InspectionFinding]
    disclaimer: str
    created_at: Optional[datetime] = None


@router.post("/inspect-file", response_model=FileInspectionResponse)
async def inspect_file_security(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Offline Static Security Inspection for APK, EXE, DLL, and other files.
    - Inspects APKs without installing.
    - Inspects EXEs without executing.
    - Operates locally without external cloud AI dependencies.
    - Flags dangerous permissions, code injection imports, high-entropy packed sections.
    - Persists file inspection record in database for audit and history tracking.
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

    raw_findings = result.get("findings", [])
    findings = [
        InspectionFinding(severity=f.get("severity", "INFO"), message=f.get("message", ""))
        for f in raw_findings
    ]

    # Persist in database
    record = FileInspection(
        user_id=current_user.id,
        filename=result.get("filename", filename),
        file_type=result.get("file_type", "Unknown"),
        file_size=len(content),
        sha256=result.get("sha256", ""),
        threat_level=result.get("threat_level", "SAFE"),
        risk_score=result.get("risk_score", 0),
        findings_json=json.dumps(raw_findings)
    )
    db.add(record)
    await db.commit()
    await db.refresh(record)

    return FileInspectionResponse(
        id=record.id,
        filename=record.filename,
        file_type=record.file_type,
        file_size=record.file_size,
        sha256=record.sha256,
        threat_level=record.threat_level,
        risk_score=record.risk_score,
        findings=findings,
        disclaimer=result.get(
            "disclaimer",
            "Heuristic static analysis inspects structural headers, sections, and symbols without executing code. It cannot guarantee detection of all malicious files."
        ),
        created_at=record.created_at
    )


@router.get("/history", response_model=List[FileInspectionResponse])
async def get_inspection_history(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Returns the current user's file security inspection history.
    """
    stmt = (
        select(FileInspection)
        .where(FileInspection.user_id == current_user.id)
        .order_by(FileInspection.created_at.desc())
        .limit(50)
    )
    res = await db.execute(stmt)
    records = res.scalars().all()

    output = []
    for r in records:
        raw = []
        if r.findings_json:
            try:
                raw = json.loads(r.findings_json)
            except Exception:
                raw = []
        findings = [
            InspectionFinding(severity=f.get("severity", "INFO"), message=f.get("message", ""))
            for f in raw
        ]
        output.append(
            FileInspectionResponse(
                id=r.id,
                filename=r.filename,
                file_type=r.file_type,
                file_size=r.file_size,
                sha256=r.sha256,
                threat_level=r.threat_level,
                risk_score=r.risk_score,
                findings=findings,
                disclaimer="Heuristic static analysis inspection history.",
                created_at=r.created_at
            )
        )
    return output
