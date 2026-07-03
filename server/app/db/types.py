"""Reusable column type helpers."""

from __future__ import annotations

from enum import StrEnum

from sqlalchemy import Enum as SAEnum


def enum_column(enum_cls: type[StrEnum], name: str) -> SAEnum:
    """A non-native (VARCHAR + CHECK) enum column.

    Stored as the enum's string *value* (not its Python name). Non-native keeps
    schema evolution simple: adding a member is an ordinary column/CHECK change
    rather than a fragile ``ALTER TYPE`` on a Postgres enum.
    """
    return SAEnum(
        enum_cls,
        name=name,
        native_enum=False,
        validate_strings=True,
        values_callable=lambda cls: [member.value for member in cls],
    )
