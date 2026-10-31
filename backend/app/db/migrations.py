import logging
from typing import Set
from sqlalchemy import inspect, text
from sqlalchemy.engine import Connection
from app.db.base import Base
import app.models  # Ensure all models are registered in Base.metadata

logger = logging.getLogger("zerochat.migrations")

# Latest known Alembic revision for schema tracking
LATEST_ALEMBIC_REVISION = "def456c789ab"


def run_idempotent_migrations(connection: Connection) -> None:
    """
    Safely inspects existing database tables and dynamically adds any missing
    columns defined in SQLAlchemy models without dropping tables or losing user data.
    
    Designed to be 100% idempotent:
    - Can be executed repeatedly without errors.
    - Preserves existing rows, foreign keys, and indexes.
    - Works on both SQLite and PostgreSQL (Supabase).
    """
    inspector = inspect(connection)
    existing_tables: Set[str] = set(inspector.get_table_names())
    dialect_name = connection.dialect.name.lower()

    for table_name, table in Base.metadata.tables.items():
        if table_name not in existing_tables:
            continue

        existing_cols = {col["name"] for col in inspector.get_columns(table_name)}

        for col in table.columns:
            if col.name not in existing_cols:
                logger.info(
                    "Detected missing column '%s' on existing table '%s'. Migrating schema...",
                    col.name,
                    table_name
                )
                
                # Compile column type specifically for the active dialect
                col_type = col.type.compile(dialect=connection.dialect)
                
                if dialect_name == "postgresql":
                    stmt = f'ALTER TABLE "{table_name}" ADD COLUMN IF NOT EXISTS "{col.name}" {col_type}'
                else:
                    # SQLite syntax: ALTER TABLE "table" ADD COLUMN "col" TYPE
                    stmt = f'ALTER TABLE "{table_name}" ADD COLUMN "{col.name}" {col_type}'

                connection.execute(text(stmt))
                logger.info("Successfully added column '%s' to '%s'.", col.name, table_name)

    # Synchronize alembic_version table if present
    if "alembic_version" in existing_tables:
        try:
            rows = connection.execute(text("SELECT version_num FROM alembic_version")).fetchall()
            if not rows:
                connection.execute(
                    text(f"INSERT INTO alembic_version (version_num) VALUES ('{LATEST_ALEMBIC_REVISION}')")
                )
            elif rows[0][0] != LATEST_ALEMBIC_REVISION:
                connection.execute(
                    text(f"UPDATE alembic_version SET version_num = '{LATEST_ALEMBIC_REVISION}'")
                )
        except Exception as e:
            logger.warning("Could not sync alembic_version: %s", e)

