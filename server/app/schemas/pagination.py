"""Pagination primitives shared by list endpoints."""

from __future__ import annotations

from pydantic import BaseModel, Field


class PageParams(BaseModel):
    """Limit/offset query parameters with sane bounds.

    Used as a FastAPI *query parameter model* — ``Annotated[PageParams, Query()]``
    — so ``?limit=&offset=`` map onto these fields.
    """

    limit: int = Field(default=20, ge=1, le=100)
    offset: int = Field(default=0, ge=0)


class Page[T](BaseModel):
    """A single page of results plus the total count for the query."""

    items: list[T]
    total: int
    limit: int
    offset: int

    @classmethod
    def create(cls, items: list[T], total: int, params: PageParams) -> Page[T]:
        return cls(items=items, total=total, limit=params.limit, offset=params.offset)
