"""User service — profile operations."""

from __future__ import annotations

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
