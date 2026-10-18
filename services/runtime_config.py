"""Runtime configuration shared by batch and future HTTP entry points."""
import os
from pathlib import Path


def runtime_path(name: str, default: Path) -> Path:
    """Keep legacy paths unless an explicit per-process override is supplied."""
    value = os.getenv(name)
    return Path(value).expanduser().resolve() if value else default


def runtime_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    if value.lower() not in {"true", "false"}:
        raise ValueError(f"{name} must be true or false")
    return value.lower() == "true"
