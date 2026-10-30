"""supabase_and_contacts_schema

Revision ID: def456c789ab
Revises: abc242b1b99b
Create Date: 2026-10-30 17:50:00.000000

"""
from typing import Sequence, Union
from alembic import op
import sqlalchemy as sa

revision: str = 'def456c789ab'
down_revision: Union[str, Sequence[str], None] = 'abc242b1b99b'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # Add profile columns to users
    conn = op.get_bind()
    inspector = sa.inspect(conn)
    existing_tables = inspector.get_table_names()

    if "users" in existing_tables:
        existing_cols = [c["name"] for c in inspector.get_columns("users")]
        if "bio" not in existing_cols:
            op.add_column("users", sa.Column("bio", sa.String(500), nullable=True))
        if "avatar_url" not in existing_cols:
            op.add_column("users", sa.Column("avatar_url", sa.String(500), nullable=True))

    # Create qr_contacts table
    if "qr_contacts" not in existing_tables:
        op.create_table(
            "qr_contacts",
            sa.Column("id", sa.Integer(), primary_key=True, index=True),
            sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
            sa.Column("contact_user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
            sa.Column("qr_payload", sa.String(500), nullable=True),
            sa.Column("nickname", sa.String(100), nullable=True),
            sa.Column("created_at", sa.DateTime(), nullable=False),
            sa.UniqueConstraint("user_id", "contact_user_id", name="uq_user_qr_contact")
        )

    # Create file_inspections table
    if "file_inspections" not in existing_tables:
        op.create_table(
            "file_inspections",
            sa.Column("id", sa.Integer(), primary_key=True, index=True),
            sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
            sa.Column("filename", sa.String(255), nullable=False),
            sa.Column("file_type", sa.String(100), nullable=False),
            sa.Column("file_size", sa.Integer(), nullable=False),
            sa.Column("sha256", sa.String(64), nullable=False, index=True),
            sa.Column("threat_level", sa.String(50), nullable=False),
            sa.Column("risk_score", sa.Integer(), nullable=False),
            sa.Column("findings_json", sa.Text(), nullable=True),
            sa.Column("created_at", sa.DateTime(), nullable=False, index=True)
        )


def downgrade() -> None:
    op.drop_table("file_inspections", if_exists=True)
    op.drop_table("qr_contacts", if_exists=True)
    with op.batch_alter_table("users") as batch_op:
        batch_op.drop_column("avatar_url")
        batch_op.drop_column("bio")

