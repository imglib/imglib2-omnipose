from typing import TYPE_CHECKING

import numpy as np
from omnipose import io, models

###############################################################################
# AUXILIARY FUNCTIONS
###############################################################################


def manage_channels_index(
    cell: int | None = None, nuclei: int | None = None
) -> list[int]:
    """Manage input channels list [cell_channel, nuclei_channel] for Omnipose.
    TODO: Rework. We must have None for single channel images."""
    if cell is not None and nuclei is not None:
        return [cell, nuclei]
    if cell is not None:
        return [cell, 0]
    if nuclei is not None:
        ## Cp doc: first channel as 0=grayscale, 1=red, 2=green, 3=blue; and set the second channel to zero, e.g. channels = [0,0] if you want to segment nuclei in grayscale or for single channel images, or channels = [3,0] if you want to segment blue nuclei.
        return [nuclei, 0]
    raise ValueError(
        "At least one of 'cell' or 'nuclei' channel must be specified by the user."
    )


###############################################################################
# PROCESSING FUNCTIONS
###############################################################################


def run_omnipose(
    img: np.ndarray, kwargs: dict
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Runs Omnipose on a single image with the given parameters.
    Refer to Omnipose documentation for kwargs list."""

    model: OmniModel | None = globals()["model"]
    if model is None:
        ## Now model should be initialize with omnipose_init script but it does not, do initialization here

        # Manage pretrained model and model type selection based on user inputs
        # - Prioritize custom model if provided
        # - Otherwise use model_name with `bact_phase_omni` as default
        custom_model = kwargs.get("custom_model", None)
        selected_model = (
            kwargs.get("model_name", "bact_phase_omni")
            if custom_model is None
            else None
        )

        task.update(
            current=2,
            maximum=5,
            message=f"Omnipose: Deploy model {selected_model if selected_model else custom_model}",
        )
        model = models.OmniModel(
            model_type=selected_model,
            pretrained_model=custom_model,
            gpu=kwargs.get("use_gpu", False),
            device=kwargs.get("device", None),
        )
        task.update(
            current=3, maximum=5, message=f"Omnipose: Predict labels (device={device})"
        )

    # Check if we need to pre-process the dimensions of the image
    channel_axis = kwargs.get("channel_axis", None)
    z_axis = kwargs.get("z_axis", None)
    time_axis = kwargs.get("time_axis", None)
    stitch_threshold = kwargs.get("stitch_threshold", 0.0)
    do_3D = kwargs.get("use_3D", False)

    if time_axis is not None and z_axis is None:
        # The only way to process T axis in batch is to fake it as a Z-axis and prevent stitching.
        z_axis = time_axis
        stitch_threshold = 0.0  # force no stitching
        do_3D = False  # force 2D processing

    # If we don't do that the image are resized like crazy, and the eval crashes.
    rescale_factor = 1.0

    result = model.eval(
        img,
        channels=kwargs.get("channels", [0, 0]),
        diameter=kwargs.get("diameter", 30),
        do_3D=do_3D,
        anisotropy=kwargs.get("anisotropy", 1.0),
        stitch_threshold=stitch_threshold,
        z_axis=z_axis,
        channel_axis=channel_axis,
        resample=kwargs.get("resample", True),
        rescale_factor=rescale_factor,
        normalize=kwargs.get("normalize", True),
        flow_threshold=kwargs.get("flow_threshold", 0.4),
        mask_threshold=kwargs.get("mask_threshold", 0.0),
        min_size=kwargs.get("min_size", 15),
        niter=kwargs.get("niter", None),
        tile_overlap=kwargs.get("tile_overlap", 0.1),
    )
    return result


def describe(obj, name="obj", indent=0):
    pad = " " * indent
    if isinstance(obj, np.ndarray):
        task.update(
            message=f"{pad}{name}: ndarray, shape={obj.shape}, dtype={obj.dtype}"
        )
    elif isinstance(obj, (list, tuple)):
        task.update(message=f"{pad}{name}: {type(obj).__name__}, len={len(obj)}")
        for i, item in enumerate(obj):
            describe(item, f"{name}[{i}]", indent + 2)
    else:
        task.update(
            message=f"{pad}{name}: {type(obj).__name__}, value={repr(obj)[:120]}"
        )


###############################################################################
# MAIN PROGRAM
###############################################################################

appose_mode = "task" in globals()
if appose_mode:
    if TYPE_CHECKING:
        from appose.python_worker import Task

        task: Task

    from appose.python_worker import Task

    task = globals()["task"]
else:
    import os

    from appose.python_worker import Task
    from cp_utils import get_torch_device

    sample_folder = "../../../samples/"  # When you run this script from its location.
    task = Task()

# load arguments and input from Appose task
if appose_mode:
    fiji_image = globals()["input"]
    fiji_output_labels = globals()["output_labels"]
    fiji_output_flows = globals()["output_flows"]

    cell_channel_index: int | None = globals()["chan1"]
    nuclei_channel_index: int | None = globals()["chan2"]
    channel_axis: int | None = globals()["channel_axis"]
    stitch_threshold: float = globals()["stitch_threshold"]
    z_axis: int = globals()["z_axis"]
    time_axis: int | None = globals()["t_axis"]
    anisotropy: float = globals()["anisotropy"]
    niter: int | None = globals()["niter"]
    use_gpu: bool = globals()["use_gpu"]

    input_image = fiji_image.ndarray()
    output_labels = fiji_output_labels.ndarray()
    output_flows = (
        fiji_output_flows.ndarray() if fiji_output_flows is not None else None
    )

    if channel_axis is None:
        channels = None
    else:
        channels = manage_channels_index(cell_channel_index, nuclei_channel_index)
    anisotropy = anisotropy if anisotropy > 0 else None

    task.update(
        current=0,
        maximum=5,
        message=f"Omnipose: Fetch input from Fiji ({input_image.shape})",
    )
else:
    test_file = "testImg_XYT.tif"
    time_axis = 0
    z_axis = None
    channel_axis = None

    file = os.path.join(sample_folder, test_file)
    input_image = io.imread(file)
    custom_model = None
    model_name = "cyto3"
    diameter = 30
    channels = [0, 1]
    use_3D = False
    stitch_threshold = 0
    anisotropy = None
    compute_flows = True
    resample = True
    normalize = True
    flow_threshold = 0.4
    mask_threshold = 0.0
    min_size = 15
    tile_overlap = 0.1
    niter = None
    use_gpu = False

use_gpu, device = get_torch_device(use_gpu)
task.update(current=1, maximum=5, message=f"Omnipose: Start Omnipose (device={device})")

# task.update(
#     message=f"Omnipose: Start Omnipose with channel_axis={channel_axis}, z_axis={z_axis}, time_axis={time_axis}")
result = run_omnipose(
    input_image,
    kwargs={
        "model_name": model_name,
        "custom_model": custom_model,
        "channels": channels,
        "diameter": diameter,
        "use_3D": use_3D,
        "stitch_threshold": stitch_threshold,
        "anisotropy": anisotropy,
        "z_axis": z_axis,
        "channel_axis": channel_axis,
        "time_axis": time_axis,
        "use_gpu": use_gpu,
        "device": device,
        "resample": resample,
        "normalize": normalize,
        "flow_threshold": flow_threshold,
        "mask_threshold": mask_threshold,
        "min_size": min_size,
        "tile_overlap": tile_overlap,
        "niter": niter,
    },
)

task.update(current=4, maximum=5, message="Omnipose: Returning results")

# Massage outputs
masks = result.masks
describe(result, "result")

if compute_flows:
    describe(result.flows, "flows")
    flows = result.flows[0].rgb
    task.update(
        message=f"Omnipose: Returning results (after flip: labels shape={masks.shape}, flows shape={flows.shape if compute_flows else 'N/A'})"
    )
    # Move the last axis (C axis) to before Y and X. There might other dims before.
    flows = np.moveaxis(flows, -1, -3) if compute_flows else None

# task.update(
#     message=f"Omnipose: Returning results (after flip: labels shape={masks.shape}, flows shape={flows.shape if compute_flows else 'N/A'})")

if appose_mode:
    # Write masks into the shared output image.
    output_labels[:] = masks
    if compute_flows:
        output_flows[:] = flows
else:
    save_path = os.path.join(sample_folder, test_file.replace(".tif", "_masks.tif"))
    io.imsave(save_path, masks.astype(np.uint16))
    if compute_flows:
        save_path = os.path.join(sample_folder, test_file.replace(".tif", "_flows.tif"))
        io.imsave(save_path, flows[0].astype(np.float32))

task.update(current=5, maximum=5, message="Omnipose: Processing completed")
