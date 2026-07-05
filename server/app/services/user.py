"""User service — profile operations."""

from __future__ import annotations

from app.models.enums import UserRole
from app.models.user import User
from app.repositories.user import UserRepository
from app.schemas.user import UserUpdate


class UserService:
    def __init__(self, users: UserRepository) -> None:
        self._users = users

    async def update_profile(self, user: User, data: UserUpdate) -> User:
        """Apply a partial profile update to ``user`` (only provided fields)."""
        changes = data.model_dump(exclude_unset=True)
        for field, value in changes.items():
            setattr(user, field, value)
        await self._users.session.flush()
        return user

    async def set_role(self, user: User, role: UserRole) -> User:
        """Demo-only: switch ``user``'s own role (see ``scripts/set_user_role.py``).

        Mirrors that script's DB write; the request-scoped session is committed by
        the ``get_session`` dependency once the request completes successfully.
        """
        user.role = role
        await self._users.session.flush()
        return user
