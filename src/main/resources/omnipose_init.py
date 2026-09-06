from typing import TYPE_CHECKING
import time
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
    model_name = "bact_phase_omni"
    use_gpu = False

use_gpu, device = get_torch_device(use_gpu)

start_time = time.time()
task.update(
    message=f"Omnipose (device={device}): deploy model {selected_model if selected_model else custom_model}",
)
model = models.OmniModel(
    model_type=selected_model, pretrained_model=custom_model, gpu=use_gpu, device=device
)
end_time = time.time()
task.update(message=f"Omnipose: Model initialized in {end_time - start_time:.2f} s.")

if appose_mode:
    task.export(model=model)
    task.export(previous_model_name=selected_model if selected_model else custom_model)
# %%
