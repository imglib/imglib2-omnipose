#!/usr/bin/env python
"""
Run Omnipose on a single multichannel TIFF (e.g. GFP bacteria).
"""

import os
import numpy as np
from PIL import Image


# ── Helpers ───────────────────────────────────────────────────────────────────

def read_tiff(path: str) -> np.ndarray:
    """Read single or multi-frame ImageJ TIFF using PIL."""
    im = Image.open(path)
    frames = []
    try:
        while True:
            frames.append(np.asarray(im.copy()))
            im.seek(im.tell() + 1)
    except EOFError:
        pass
    return np.stack(frames) if len(frames) > 1 else frames[0]


def save_mask(path: str, mask: np.ndarray) -> None:
    Image.fromarray(mask.astype(np.uint16)).save(path)


# ── GPU ───────────────────────────────────────────────────────────────────────

from omnipose.gpu import use_gpu
device, use_GPU = use_gpu()
print(f"GPU: {device}  available: {use_GPU}")


# ── Parameters ────────────────────────────────────────────────────────────────

INPUT_FILE  = "samples/Stage_1_stableGFP_Spectinomycin_higherconc_z6-1.tif"
OUTPUT_DIR  = "results"

# MODEL_TYPE  =  "cyto2_omni" 
MODEL_TYPE  = "bact_fluor_omni"

# Which of the 3 channels (0-indexed) is the GFP signal?
# CHAN1 = 1   # <-- adjust: 0, 1, or 2
CHANNELS = [ 2, 0 ]
DIAMETER    = 5.
MASK_THRESH = 0.0
FLOW_THRESH = 0.0


# ── Load and prepare image ────────────────────────────────────────────────────

raw = read_tiff(INPUT_FILE)
print(f"Raw shape: {raw.shape}  dtype: {raw.dtype}")

# raw is (3, H, W) — 3 channel planes from PIL frame stacking.
# Transpose to (H, W, 3) so cellpose's channel indexing works correctly.
assert raw.ndim == 3, f"Expected (C, H, W), got {raw.shape}"
img = raw.transpose(1, 2, 0)          # → (H, W, 3)

# input = img[:, :, CHAN1].astype(np.float32)
input = img
print(f"Model input shape: {input.shape}")

# ── Run Omnipose ──────────────────────────────────────────────────────────────

from cellpose_omni import models

model = models.CellposeModel(gpu=use_GPU, model_type=MODEL_TYPE, nchan=2)
params = {'channels':CHANNELS, # always define this if using older models, e.g. [0,0] with bact_phase_omni
        #   'diameter': DIAMETER, # if None, will be estimated from data
          'rescale': True, # upscale or downscale your images, None = no rescaling 
          'mask_threshold': MASK_THRESH, # erode or dilate masks with higher or lower values between -5 and 5 
          'flow_threshold': FLOW_THRESH, # default is .4, but only needed if there are spurious masks to clean up; slows down output
          'omni': True, # we can turn off Omnipose mask reconstruction, not advised 
          'cluster': True, # use DBSCAN clustering
          'resample': True, # whether or not to run dynamics on rescaled grid or original grid 
          'verbose': False, # turn on if you want to see more output 
          'tile': False, # average the outputs from flipped (augmented) images; slower, usually not needed 
          'niter': None, # default None lets Omnipose calculate # of Euler iterations (usually <20) but you can tune it for over/under segmentation 
          'augment': False, # Can optionally rotate the image and average network outputs, usually not needed 
          'affinity_seg': True, # new feature, stay tuned...
         }

output = model.eval(input, **params)
mask = output[0]  # (H, W) uint16 mask of object labels
flow = output[1]  # (H, W, 2) float32 flow field

print(f"Found {int(mask.max())} object(s).  Mask shape: {mask.shape}")


# ── Save ──────────────────────────────────────────────────────────────────────

os.makedirs(OUTPUT_DIR, exist_ok=True)
base = os.path.splitext(os.path.basename(INPUT_FILE))[0]
out  = os.path.join(OUTPUT_DIR, f"{base}_masks.tif")
save_mask(out, mask)
print(f"Saved → {out}")

