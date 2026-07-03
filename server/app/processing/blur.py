"""Blur detection (OpenCV).

Uses the variance of the Laplacian: a sharp image has lots of high-frequency
edge content (high variance); a blurry one is smooth (low variance). Frames
scoring below a configured threshold are dropped from the pipeline.
"""

from __future__ import annotations

import cv2
import numpy as np

from app.core.exceptions import ValidationError


def laplacian_variance(data: bytes) -> float:
    """Return the focus measure (variance of the Laplacian) of an image."""
    array = np.frombuffer(data, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_GRAYSCALE)
    if image is None:
        raise ValidationError("Could not decode image for blur analysis.")
    return float(cv2.Laplacian(image, cv2.CV_64F).var())


def is_blurry(data: bytes, *, threshold: float) -> tuple[bool, float]:
    score = laplacian_variance(data)
    return score < threshold, score
