import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from app.api.v1.router import api_router
from app.core.config import settings
from app.db.base import Base
from app.db.session import AsyncSessionLocal, engine
from app.services.admin_service import AdminService
from app.services.email_service import EmailService

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("zerochat.main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. Initialize database tables
    logger.info("Initializing database schema...")
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    logger.info("Database schema initialized.")

    # 2. Check SMTP status
    if EmailService.is_configured():
        logger.info("SMTP service: ENABLED (Host: %s:%s, User: %s)", settings.SMTP_HOST, settings.SMTP_PORT, settings.SMTP_USER)
    else:
        logger.warning(
            "SMTP service is NOT CONFIGURED. Live Gmail delivery is inactive. %s",
            EmailService.get_configuration_diagnostics()
        )

    # 3. Secure Founder Account Provisioning
    if settings.FOUNDER_AUTO_PROVISION:
        async with AsyncSessionLocal() as db:
            founder_pwd = settings.FOUNDER_PASSWORD or "Founder@ZeroChat2026!"
            founder = await AdminService.provision_founder_account(
                db=db,
                email=settings.FOUNDER_EMAIL,
                password=founder_pwd,
                username=settings.FOUNDER_USERNAME
            )
            logger.info(
                "Founder account provisioned: %s (Role: %s, Verified Badge: %s)",
                founder.email,
                founder.role,
                founder.is_verified_badge
            )

    yield

    # Teardown
    await engine.dispose()
    logger.info("Backend application shutdown complete.")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="ZeroChat AI Authentication, Security & Founder Admin Service",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", tags=["Health"])
async def health_check():
    return {
        "status": "healthy",
        "app": settings.APP_NAME,
        "version": settings.APP_VERSION,
        "smtp_configured": EmailService.is_configured(),
        "founder_email": settings.FOUNDER_EMAIL
    }


import os
from fastapi.staticfiles import StaticFiles

# Create uploads directory if not exists
os.makedirs("uploads", exist_ok=True)
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")

# Include API routers
app.include_router(api_router, prefix="/api")

