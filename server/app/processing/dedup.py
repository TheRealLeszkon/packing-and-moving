"""Perceptual-hash duplicate detection (ImageHash).

Adjacent video frames are often near-identical. We compute a perceptual hash
(pHash) per frame and drop frames whose hash is within a small Hamming distance
of an already-kept frame, keeping the inventory's evidence set compact.
"""

from __future__ import annotations

import io

import imagehash
from PIL import Image


def perceptual_hash(data: bytes) -> imagehash.ImageHash:
    with Image.open(io.BytesIO(data)) as img:
        return imagehash.phash(img)


class DuplicateFilter:
    """Stateful filter: remembers kept hashes and rejects near-duplicates."""

    def __init__(self, *, hamming_threshold: int) -> None:
        self._threshold = hamming_threshold
        self._seen: list[imagehash.ImageHash] = []

    def is_duplicate(self, data: bytes) -> bool:
        candidate = perceptual_hash(data)
        for kept in self._seen:
            if (candidate - kept) <= self._threshold:
                return True
        self._seen.append(candidate)
        return False
