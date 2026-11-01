import os
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"
os.environ["FOUNDER_AUTO_PROVISION"] = "false"

import asyncio
import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from app.core.config import settings
settings.RESEND_API_KEY = None
settings.SMTP_ENABLED = False
from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.services.admin_service import AdminService

TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

test_engine = create_async_engine(
    TEST_DATABASE_URL,
    connect_args={"check_same_thread": False}
)

TestingSessionLocal = async_sessionmaker(
    bind=test_engine,
    class_=AsyncSession,
    expire_on_commit=False
)

# Patch app session and engine for testing isolation
import app.db.session as session_module
session_module.engine = test_engine
session_module.AsyncSessionLocal = TestingSessionLocal
import app.main as main_module
main_module.engine = test_engine
main_module.AsyncSessionLocal = TestingSessionLocal
from contextlib import asynccontextmanager
@asynccontextmanager
async def test_lifespan(app):
    yield
app.lifespan_context = test_lifespan
app.router.lifespan_context = test_lifespan





@pytest_asyncio.fixture(scope="function")
async def db_session():
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with TestingSessionLocal() as session:
        yield session

    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture(scope="function")
async def client(db_session):
    async def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as ac:
        yield ac

    app.dependency_overrides.clear()


@pytest_asyncio.fixture(scope="function")
async def founder_user(db_session):
    return await AdminService.provision_founder_account(
        db=db_session,
        email=settings.FOUNDER_EMAIL,
        password="FounderSecret123!",
        username="FounderAdmin"
    )

