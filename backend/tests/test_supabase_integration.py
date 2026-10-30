import pytest
from app.core.config import Settings
from app.services.supabase_storage import SupabaseStorageService


def test_supabase_database_url_normalization():
    # Test normalization of standard Supabase PostgreSQL URLs
    s1 = Settings(DATABASE_URL="postgres://postgres:mypassword@db.myproject.supabase.co:5432/postgres")
    assert s1.async_database_url.startswith("postgresql+asyncpg://")

    s2 = Settings(DATABASE_URL="postgresql://postgres.myproject:mypassword@aws-0-eu.pooler.supabase.com:6543/postgres")
    assert s2.async_database_url.startswith("postgresql+asyncpg://")

    s3 = Settings(DATABASE_URL="sqlite+aiosqlite:///./zerochat.db")
    assert s3.async_database_url == "sqlite+aiosqlite:///./zerochat.db"


def test_supabase_storage_service_configuration():
    # When SUPABASE_URL is not set, is_configured should be False
    assert SupabaseStorageService.is_configured() is False


@pytest.mark.asyncio
async def test_health_check_supabase_diagnostics(client):
    res = await client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert "database_backend" in data
    assert "supabase_configured" in data
    assert "supabase_storage_configured" in data
    assert data["status"] == "healthy"

