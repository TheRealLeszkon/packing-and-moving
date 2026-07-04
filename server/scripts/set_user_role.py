"""Set a user's role by email (admin utility).

Roles (SCHEDULED/surveyor/admin flows are gated on this) are assigned server-side,
so there is no API to self-promote. Use this locally to grant surveyor/admin.

Run from ``server/``::

    uv run python scripts/set_user_role.py <email> <customer|surveyor|admin>

Example::

    uv run python scripts/set_user_role.py jmkevin2006@gmail.com surveyor
"""

from __future__ import annotations

import asyncio
import os
import sys

_SERVER_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _SERVER_ROOT not in sys.path:
    sys.path.insert(0, _SERVER_ROOT)


async def set_role(email: str, role_value: str) -> None:
    from sqlalchemy import select

    from app.db.session import SessionFactory
    from app.models.enums import UserRole
    from app.models.user import User

    try:
        role = UserRole(role_value)
    except ValueError:
        valid = ", ".join(r.value for r in UserRole)
        print(f"ERROR: invalid role '{role_value}'. Valid: {valid}", file=sys.stderr)
        raise SystemExit(2) from None

    async with SessionFactory() as session:
        user = (await session.execute(select(User).where(User.email == email))).scalar_one_or_none()
        if user is None:
            print(f"ERROR: no user with email {email}", file=sys.stderr)
            raise SystemExit(1)
        old = user.role.value
        user.role = role
        await session.commit()
        print(f"OK: {email} role {old} -> {user.role.value} (id={user.id})")


def main() -> None:
    if len(sys.argv) != 3:
        print(__doc__)
        raise SystemExit(2)
    asyncio.run(set_role(sys.argv[1], sys.argv[2]))


if __name__ == "__main__":
    main()
