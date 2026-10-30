import os
os.environ["DATABASE_URL"] = "sqlite+aiosqlite:///:memory:"
os.environ["FOUNDER_AUTO_PROVISION"] = "false"
os.environ["SMTP_ENABLED"] = "false"

