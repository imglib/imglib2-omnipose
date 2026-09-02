from typing import TYPE_CHECKING

from omnipose import models

report = print


def listen(callback):
    global report
    report = callback


appose_mode = "task" in globals()
if appose_mode:
    task.update(message="Appose mode")

    if TYPE_CHECKING:
        from appose.python_worker import Task

        task: Task

    from appose.python_worker import Task

    task = globals()["task"]
    listen(task.update)
else:
    task.update(message="Standalone mode")

    from appose.python_worker import Task
    from omnipose_utils import get_torch_device

    sample_folder = "../../../samples/"  # When you run this script from its location.
    task = Task()

# load arguments and input from Appose task
if appose_mode:
    use_gpu: bool = globals()["use_gpu"]
    selected_model = model_name if custom_model is None else None
else:
    custom_model = None
    model_name = "cyto3"
    use_gpu = False

use_gpu, device = get_torch_device(use_gpu)

task.update(
    current=1,
    maximum=2,
    message=f"Omnipose: Start Omnipose (device={device}): deploy model {selected_model if selected_model else custom_model}",
)

model = models.OmniModel(
    model_type=selected_model, pretrained_model=custom_model, gpu=use_gpu, device=device
)

task.update(current=2, maximum=2, message="Omnipose: Model initialized")

if appose_mode:
    task.export(model=model)
# %%
