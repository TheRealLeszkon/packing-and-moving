"""User request/response schemas."""

from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.enums import UserRole

# Roles a user may self-assign via the demo role switch (admin is never one).
_SELF_ASSIGNABLE_ROLES = (UserRole.CUSTOMER, UserRole.SURVEYOR)


class UserResponse(BaseModel):
    """Public representation of a user."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str
    name: str | None
    role: UserRole
    is_active: bool
    created_at: datetime


class UserUpdate(BaseModel):
    """Partial update for the current user's own profile.

    Only fields a user may change about themselves. Role, email and account
    status are intentionally excluded (changed via admin flows, not self-service).
    """

    name: str | None = Field(default=None, min_length=1, max_length=255)


class RoleUpdate(BaseModel):
    """Demo-only self-service role switch.

    Only the two operational roles may be self-assigned; ``admin`` (privilege
    escalation) and any other value are rejected with a 422.
    """

    role: UserRole

    @field_validator("role")
    @classmethod
    def _reject_privileged(cls, value: UserRole) -> UserRole:
        if value not in _SELF_ASSIGNABLE_ROLES:
            allowed = ", ".join(r.value for r in _SELF_ASSIGNABLE_ROLES)
            raise ValueError(f"role must be one of: {allowed}")
        return value
