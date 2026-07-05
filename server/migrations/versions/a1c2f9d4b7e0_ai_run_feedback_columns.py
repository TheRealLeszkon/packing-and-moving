"""ai run feedback columns

Revision ID: a1c2f9d4b7e0
Revises: 51daa58f2e78
Create Date: 2026-07-05 12:00:00.000000

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = 'a1c2f9d4b7e0'
down_revision: str | Sequence[str] | None = '51daa58f2e78'
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Upgrade schema."""
    op.add_column('ai_analysis_runs', sa.Column('needs_more_images', sa.Boolean(), nullable=True))
    op.add_column(
        'ai_analysis_runs',
        sa.Column('requested_images', postgresql.JSONB(astext_type=sa.Text()), nullable=True),
    )
    # Backfill from the stored raw provider response where available.
    op.execute(
        """
        UPDATE ai_analysis_runs
        SET needs_more_images = (raw_response ->> 'needsMoreImages')::boolean,
            requested_images = raw_response -> 'requestedImages'
        WHERE raw_response IS NOT NULL
        """
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_column('ai_analysis_runs', 'requested_images')
    op.drop_column('ai_analysis_runs', 'needs_more_images')
