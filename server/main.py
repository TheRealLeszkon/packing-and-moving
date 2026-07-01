import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes import router
from app.database.engine import create_db_and_tables

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(name)s — %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    create_db_and_tables()
    yield


app = FastAPI(
    title="Packing & Moving Survey API",
    description="AI-assisted survey system for relocation assessments powered by Gemini.",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(router)
