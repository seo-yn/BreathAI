"""Runtime configuration shared by batch and future HTTP entry points."""
import os
from pathlib import Path


def llm_memory_options():
    """Opt-in GPU budget, leaving room for CE and generation activations."""
    gpu = os.getenv('TTOBAGI_LLM_GPU_MEMORY')
    if not gpu:
        return {}
    folder = runtime_path('TTOBAGI_OFFLOAD_DIR', Path('/workspace/breathai-cache/offload'))
    folder.mkdir(parents=True, exist_ok=True)
    return dict(max_memory={0: gpu, 'cpu': os.getenv('TTOBAGI_LLM_CPU_MEMORY', '8GiB')},
                offload_folder=str(folder), offload_state_dict=True, low_cpu_mem_usage=True)


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
