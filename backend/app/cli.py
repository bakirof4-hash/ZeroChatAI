import argparse
import asyncio
import sys
from app.db.base import Base
from app.db.session import AsyncSessionLocal, engine
from app.services.admin_service import AdminService
from app.services.email_service import EmailService


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)


async def create_founder(email: str, password: str, username: str):
    await init_db()
    async with AsyncSessionLocal() as db:
        user = await AdminService.provision_founder_account(
            db=db,
            email=email,
            password=password,
            username=username
        )
        print(f"\n[SUCCESS] Founder account successfully provisioned!")
        print(f"  Email: {user.email}")
        print(f"  Username: {user.username}")
        print(f"  Role: {user.role} (Super Admin)")
        print(f"  Blue Verified Badge: {user.is_verified_badge}")
        print(f"  Owner Status: is_founder = {user.is_founder}")
        print("Ordinary users cannot modify or revoke this role.")


def check_smtp():
    print("\nChecking SMTP Configuration Status:")
    configured = EmailService.is_configured()
    if configured:
        print("  Status: CONFIGURED AND READY")
    else:
        print("  Status: NOT FULLY CONFIGURED")
        print(EmailService.get_configuration_diagnostics())


def main():
    parser = argparse.ArgumentParser(description="ZeroChat AI Backend Security & Founder Management CLI")
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # create-founder command
    founder_parser = subparsers.add_parser("create-founder", help="Provision the Founder Super Admin account")
    founder_parser.add_argument("--email", required=True, help="Founder Gmail / email address")
    founder_parser.add_argument("--password", required=True, help="Strong account password (min 8 chars)")
    founder_parser.add_argument("--username", default="ZeroFounder", help="Display username")

    # check-smtp command
    subparsers.add_parser("check-smtp", help="Verify SMTP settings and display setup instructions")

    args = parser.parse_args()

    if args.command == "create-founder":
        if len(args.password) < 8:
            print("[ERROR] Password must be at least 8 characters long.")
            sys.exit(1)
        asyncio.run(create_founder(args.email, args.password, args.username))
    elif args.command == "check-smtp":
        check_smtp()
    else:
        parser.print_help()


if __name__ == "__main__":
    main()

