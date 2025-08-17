package com.example.raw_pesms.data.AI

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import kotlin.math.sqrt
import kotlinx.coroutines.tasks.await

class FaceRecognitionManager(context: Context) {

    // Tune this per your model
    val inputSize = 160
    val matchThreshold = 0.5f // cosine similarity threshold (0..1). Adjust as needed.

    private val interpreter: Interpreter by lazy {
        val model = FileUtil.loadMappedFile(context, "facenet.tflite")
        Interpreter(model, Interpreter.Options())
    }

    private val detector: FaceDetector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(opts)
    }

    suspend fun detectFaces(bitmap: Bitmap): List<Bitmap> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = detector.process(image).await()
        if (faces.isEmpty()) return emptyList()

        return faces.mapNotNull { face ->
            crop(bitmap, face.boundingBox)?.let { cropBmp ->
                Bitmap.createScaledBitmap(cropBmp, inputSize, inputSize, true)
            }
        }
    }

    fun embed(faceBitmap: Bitmap): FloatArray? {
        // FaceNet expects normalized float32 [0,1] or [-1,1], depends on model; here use [0,1]
        val input = preprocess(faceBitmap)
        val output = Array(1) { FloatArray(128) } // 128-d embedding typical
        interpreter.run(input, output)
        // L2 normalize (common for FaceNet)
        return l2Normalize(output[0])
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na.toDouble()).toFloat() * sqrt(nb.toDouble()).toFloat()
        return if (denom == 0f) 0f else dot / denom
    }

    private fun preprocess(bmp: Bitmap): Array<Array<Array<FloatArray>>> {
        val w = bmp.width; val h = bmp.height
        val input = Array(1) { Array(h) { Array(w) { FloatArray(3) } } }
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[idx++]
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                input[0][y][x][0] = r
                input[0][y][x][1] = g
                input[0][y][x][2] = b
            }
        }
        return input
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var s = 0f; for (x in v) s += x * x
        val n = sqrt(s.toDouble()).toFloat()
        if (n == 0f) return v
        for (i in v.indices) v[i] = v[i] / n
        return v
    }

    private fun crop(src: Bitmap, rect: Rect): Bitmap? {
        val x = rect.left.coerceAtLeast(0)
        val y = rect.top.coerceAtLeast(0)
        val w = rect.width().coerceAtMost(src.width - x)
        val h = rect.height().coerceAtMost(src.height - y)
        if (w <= 0 || h <= 0) return null
        return Bitmap.createBitmap(src, x, y, w, h)
    }
}
