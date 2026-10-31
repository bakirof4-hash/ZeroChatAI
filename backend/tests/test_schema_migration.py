import pytest
from sqlalchemy import create_engine, text, inspect
from app.db.migrations import run_idempotent_migrations, LATEST_ALEMBIC_REVISION
from app.db.base import Base


def test_idempotent_migrations_on_sqlite():
    # 1. Create a test engine with an older schema (users table missing bio and avatar_url)
    engine = create_engine("sqlite:///:memory:")
    with engine.begin() as conn:
        conn.execute(text("""
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                email VARCHAR(255) UNIQUE NOT NULL,
                username VARCHAR(100) UNIQUE NOT NULL,
                hashed_password VARCHAR(255) NOT NULL,
                role VARCHAR(50) NOT NULL,
                is_founder BOOLEAN NOT NULL,
                is_verified_badge BOOLEAN NOT NULL,
                is_active BOOLEAN NOT NULL,
                email_verified BOOLEAN NOT NULL,
                created_at DATETIME NOT NULL,
                last_login_at DATETIME
            )
        """))
        # Insert a test user to ensure existing data is not deleted
        conn.execute(text("""
            INSERT INTO users (id, email, username, hashed_password, role, is_founder, is_verified_badge, is_active, email_verified, created_at)
            VALUES (1, 'founder@zerochat.ai', 'Founder', 'hash', 'SUPER_ADMIN', 1, 1, 1, 1, '2026-10-30 00:00:00')
        """))
        conn.execute(text("CREATE TABLE alembic_version (version_num VARCHAR(32) PRIMARY KEY)"))

    # 2. Run idempotent migrations
    with engine.begin() as conn:
        run_idempotent_migrations(conn)

    # 3. Verify columns were added without deleting user
    with engine.begin() as conn:
        inspector = inspect(conn)
        cols = {c["name"] for c in inspector.get_columns("users")}
        assert "bio" in cols, "Column 'bio' should have been added"
        assert "avatar_url" in cols, "Column 'avatar_url' should have been added"
        
        user_row = conn.execute(text("SELECT email, bio, avatar_url FROM users WHERE id = 1")).fetchone()
        assert user_row[0] == "founder@zerochat.ai"
        assert user_row[1] is None  # bio defaults to NULL
        assert user_row[2] is None  # avatar_url defaults to NULL

        rev_row = conn.execute(text("SELECT version_num FROM alembic_version")).fetchone()
        assert rev_row[0] == LATEST_ALEMBIC_REVISION

    # 4. Verify idempotency by running a second time
    with engine.begin() as conn:
        run_idempotent_migrations(conn)
        cols_after = {c["name"] for c in inspect(conn).get_columns("users")}
        assert "bio" in cols_after
        assert "avatar_url" in cols_after

