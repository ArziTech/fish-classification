package com.example.fishclassification.ml

import android.graphics.RectF

object PostProcessor {

    /**
     * Parses YOLOv11 raw output into a list of [Detection] objects after NMS.
     *
     * Expected raw output shape: [1, 4+nc, num_anchors] or [1, num_anchors, 4+nc].
     * The heuristic: the smaller of the last two dimensions is treated as `4+nc`.
     * If `rawOutput[0].size` < `rawOutput[0][0].size` → shape is [1, 4+nc, anchors] (standard).
     * Otherwise → shape is [1, anchors, 4+nc] and we transpose.
     *
     * Channels 0..3: cx, cy, w, h in normalized [0,1] input-space coords.
     * Channels 4..(4+nc-1): class scores (already sigmoid'd in YOLOv11 export).
     */
    fun parseDetections(
        rawOutput: Array<Array<FloatArray>>,
        labels: List<String>,
        confidenceThreshold: Float = 0.25f,
        iouThreshold: Float = 0.45f,
        inputSize: Int = 640,
    ): List<Detection> {
        if (rawOutput.isEmpty() || rawOutput[0].isEmpty() || labels.isEmpty()) return emptyList()

        val batch = rawOutput[0]
        val dim1 = batch.size          // either (4+nc) or num_anchors
        val dim2 = batch[0].size       // the other dimension

        // Heuristic: smaller dimension = 4+nc
        val isStandard = dim1 <= dim2  // [4+nc, anchors]
        val numChannels = if (isStandard) dim1 else dim2
        val numAnchors = if (isStandard) dim2 else dim1
        val nc = numChannels - 4

        if (nc <= 0 || nc != labels.size) {
            // Mismatch — return empty rather than crash
            return emptyList()
        }

        val detections = mutableListOf<Detection>()
        val inputSizeF = inputSize.toFloat()

        for (a in 0 until numAnchors) {
            // Extract bbox and class scores depending on layout
            val cx: Float
            val cy: Float
            val w: Float
            val h: Float
            val scores: FloatArray

            if (isStandard) {
                // batch[channel][anchor]
                cx = batch[0][a]
                cy = batch[1][a]
                w  = batch[2][a]
                h  = batch[3][a]
                scores = FloatArray(nc) { c -> batch[4 + c][a] }
            } else {
                // batch[anchor][channel]
                cx = batch[a][0]
                cy = batch[a][1]
                w  = batch[a][2]
                h  = batch[a][3]
                scores = FloatArray(nc) { c -> batch[a][4 + c] }
            }

            // Find best class
            var bestClass = 0
            var bestScore = scores[0]
            for (c in 1 until nc) {
                if (scores[c] > bestScore) {
                    bestScore = scores[c]
                    bestClass = c
                }
            }

            if (bestScore < confidenceThreshold) continue

            // Convert cx,cy,w,h (normalized) to pixel RectF
            val left   = (cx - w / 2f) * inputSizeF
            val top    = (cy - h / 2f) * inputSizeF
            val right  = (cx + w / 2f) * inputSizeF
            val bottom = (cy + h / 2f) * inputSizeF

            detections.add(
                Detection(
                    classIndex = bestClass,
                    className = labels.getOrElse(bestClass) { "class_$bestClass" },
                    confidence = bestScore,
                    boundingBox = RectF(left, top, right, bottom),
                )
            )
        }

        if (detections.isEmpty()) return emptyList()

        // Apply NMS per class, then collect results
        val result = mutableListOf<Detection>()
        val classes = detections.map { it.classIndex }.toSet()
        for (cls in classes) {
            val clsDetections = detections.filter { it.classIndex == cls }
            result.addAll(nms(clsDetections, iouThreshold))
        }

        return result.sortedByDescending { it.confidence }
    }

    /**
     * Returns the highest-confidence detection as a (classIndex, confidence) pair,
     * or null if the list is empty.
     */
    fun pickTopResult(detections: List<Detection>): Pair<Int, Float>? {
        if (detections.isEmpty()) return null
        val top = detections.maxByOrNull { it.confidence } ?: return null
        return Pair(top.classIndex, top.confidence)
    }

    /**
     * Standard greedy Non-Maximum Suppression.
     * Input list should be for a single class (or IoU-only NMS).
     */
    fun nms(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        if (detections.size <= 1) return detections

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val kept = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best.boundingBox, it.boundingBox) >= iouThreshold }
        }

        return kept
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft   = maxOf(a.left, b.left)
        val interTop    = maxOf(a.top, b.top)
        val interRight  = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interW = interRight - interLeft
        val interH = interBottom - interTop
        if (interW <= 0f || interH <= 0f) return 0f

        val interArea = interW * interH
        val aArea = (a.right - a.left) * (a.bottom - a.top)
        val bArea = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = aArea + bArea - interArea

        return if (unionArea <= 0f) 0f else interArea / unionArea
    }
}
