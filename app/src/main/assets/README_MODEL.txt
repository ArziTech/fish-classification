YOLOV11 TFLITE MODEL PLACEHOLDER
=================================

Place your trained YOLOv11 quantized .tflite model file here as:

    app/src/main/assets/yolov11_fp16.tflite

The build is configured to NOT compress .tflite files (via `noCompress += "tflite"`
in app/build.gradle.kts), which allows the TensorFlow Lite interpreter to
memory-map (mmap) the model directly from the APK for efficient loading.

MODEL REQUIREMENTS
------------------
- Format: TensorFlow Lite (.tflite), preferably quantized (int8 or float16)
- Task: image classification for fish species
- Input: RGB image tensor (640x640x3 )
- Output: class scores matching the labels in labels.txt

LABELS
------
Update `labels.txt` in this same assets/ directory to match the class names
your model was trained on, one label per line, in the same order as model output indices.

The model file is NOT included in version control. You must provide it separately.
