"""User model."""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import String
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.base import Base, TimestampMixin, UUIDPrimaryKeyMixin
from app.db.types import enum_column
from app.models.enums import UserRole

if TYPE_CHECKING:
    from app.models.refresh_token import RefreshToken
    from app.models.survey import Survey


class User(Base, UUIDPrimaryKeyMixin, TimestampMixin):
    __tablename__ = "users"

    email: Mapped[str] = mapped_column(String(320), unique=True, index=True)
    name: Mapped[str | None] = mapped_column(String(255))
    role: Mapped[UserRole] = mapped_column(
        enum_column(UserRole, "user_role"),
        default=UserRole.CUSTOMER,
        index=True,
    )
    # Google's stable subject identifier ("sub"), populated on first OAuth sign-in
    # (Phase 2). Nullable so users can exist before OAuth is wired up.
    google_sub: Mapped[str | None] = mapped_column(String(255), unique=True, index=True)
    is_active: Mapped[bool] = mapped_column(default=True)

    refresh_tokens: Mapped[list[RefreshToken]] = relationship(
        back_populates="user",
        cascade="all, delete-orphan",
    )
    # Surveys this user owns as the customer.
    surveys_as_customer: Mapped[list[Survey]] = relationship(
        back_populates="customer",
        foreign_keys="Survey.customer_id",
    )
    # Surveys assigned to this user as the surveyor.
    surveys_as_surveyor: Mapped[list[Survey]] = relationship(
        back_populates="surveyor",
        foreign_keys="Survey.surveyor_id",
    )
