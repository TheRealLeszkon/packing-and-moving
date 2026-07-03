"""ORM model registry.

Importing this package must import every model module so their tables are
registered on ``Base.metadata`` before Alembic autogeneration runs. Models are
added here in Phase 1.
"""

from __future__ import annotations

__all__: list[str] = []
