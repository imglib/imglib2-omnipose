# These imports are required for Appose calls to work on Windows platforms.
import numpy as np

import torch

def get_torch_device(use_gpu: bool) -> tuple[bool, torch.device]:
    """Check torch device availability and returns a tupple (use_gpu: bool, device: torch.device) using the best available backend: CUDA > MPS > CPU."""
    if not use_gpu:
        return False, torch.device("cpu")

    if torch.cuda.is_available():
        return True, torch.device("cuda")
    
    if torch.backends.mps.is_available():
        return True, torch.device("mps")

    return False, torch.device("cpu")

# %%
