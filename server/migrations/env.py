"""Alembic environment, wired to the application's settings and metadata.

The database URL is taken from the app ``Settings`` (single source of truth) so
migrations and the running app can never drift apart. ``target_metadata`` points
at the app's declarative ``Base``; importing ``app.models`` registers every model
on that metadata for ``--autogenerate``.
"""

import asyncio
from logging.config import fileConfig

from alembic import context
from sqlalchemy import pool
from sqlalchemy.engine import Connection
from sqlalchemy.ext.asyncio import async_engine_from_config

import app.models  # noqa: F401  -- registers all ORM models on Base.metadata
from app.core.config import settings
from app.db.base import Base

config = context.config

# Inject the app's async DB URL (render_as_string keeps the password intact for
# the driver but Alembic never logs it).
config.set_main_option(
    "sqlalchemy.url",
    settings.sqlalchemy_url.render_as_string(hide_password=False),
)

if config.config_file_name is not None:
    fileConfig(config.config_file_name)

target_metadata = Base.metadata


def include_name(name: str | None, type_: str, parent_names: dict[str, str | None]) -> bool:
    """Restrict autogenerate to tables this app owns.

    The dev database is shared with unrelated tables (other projects + old POC
    leftovers). Without this filter, autogenerate would emit ``DROP TABLE`` for
    every table not in our metadata. We only ever manage our own tables (plus
    Alembic's own ``alembic_version``).
    """
    if type_ == "table":
        return name in target_metadata.tables
    return True


# Shared configure() options for both offline and online runs.
_CONFIGURE_OPTS: dict[str, object] = {
    "target_metadata": target_metadata,
    "compare_type": True,
    "compare_server_default": True,
    "include_name": include_name,
    # Only reflect our tables, so foreign tables never enter the comparison.
    "include_schemas": False,
}


def run_migrations_offline() -> None:
    """Emit SQL to stdout without a live connection."""
    context.configure(
        url=config.get_main_option("sqlalchemy.url"),
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        **_CONFIGURE_OPTS,
    )
    with context.begin_transaction():
        context.run_migrations()


def do_run_migrations(connection: Connection) -> None:
    context.configure(connection=connection, **_CONFIGURE_OPTS)
    with context.begin_transaction():
        context.run_migrations()


async def run_async_migrations() -> None:
    connectable = async_engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()


def run_migrations_online() -> None:
    asyncio.run(run_async_migrations())


if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
